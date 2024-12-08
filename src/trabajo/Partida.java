package trabajo;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class Partida implements Runnable {

    private ExecutorService pool = Executors.newFixedThreadPool(4); // Pool de hilos para ejecutar tareas concurrentes.
    private Map<String, List<File>> canciones = new HashMap<>(); // Mapa que organiza canciones por categorías.

    private Object[] claves; // Arreglo de claves de las categorías del mapa canciones.
    private String claveCancion; // Clave seleccionada aleatoriamente para el juego.
    private volatile boolean cancionAcertada = false; // Indicador de si la canción actual ha sido acertada.

    private ArrayList<Jugador> jugadores = new ArrayList<>(); // Lista de jugadores en la partida.
    private AtomicReference<Jugador> ganadorActual = new AtomicReference<>(); // Jugador que ha acertado la canción.
    private AtomicReference<Long> tiempoGanador = new AtomicReference<>(Long.MAX_VALUE); // Tiempo mínimo en el que se acertó.

    private int turno = 1; // Número del turno actual.
    private boolean modoJuego1 = false; // Indicador del modo de juego (true = modo 1, false = modo 2).
    private List<Socket> sockets = new ArrayList<>(); // Lista de sockets para la conexión con los jugadores.
    private List<BufferedReader> ins = new ArrayList<>(); // Flujos de entrada de los jugadores.
    private List<PrintWriter> outs = new ArrayList<>(); // Flujos de salida hacia los jugadores.

    public Partida(ArrayList<Jugador> jugadores) {
        this.jugadores = jugadores; // Inicializa la lista de jugadores.
        jugadores.forEach(jugador -> {
            try {
                // Configura los sockets y los flujos de entrada/salida de cada jugador.
                sockets.add(jugador.getSocket());
                ins.add(new BufferedReader(new InputStreamReader(jugador.getSocket().getInputStream())));
                outs.add(new PrintWriter(jugador.getSocket().getOutputStream(), true));
            } catch (IOException e) {
                e.printStackTrace(); // Maneja errores de E/S.
            }
        });
    }

    public void escribir(PrintWriter out, String mensaje) {
        // Envía un mensaje al cliente y fuerza el vaciado del flujo.
        out.println(mensaje);
        out.flush();
    }

    public void recibirNombres(BufferedReader in, Jugador j, CountDownLatch cd) {
        try {
            // Lee el nombre del jugador desde el flujo de entrada y lo asigna.
            String nombre = in.readLine();
            j.setName(nombre);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            cd.countDown(); // Decrementa el CountDownLatch para indicar que se completó el proceso.
        }
    }

    public void aniadirCanciones() {
        // Añade listas de canciones clasificadas por categoría.
    	// Solo 2 canciones por cada playlist, por simplicidad a la hora de probar el código.
        List<File> cancionesRock = new ArrayList<>();
        List<File> cancionesPopEsp = new ArrayList<>();
        List<File> cancionesRapEsp = new ArrayList<>();

        cancionesPopEsp.add(new File("./resources/zapatillas.mp3"));
        cancionesPopEsp.add(new File("./resources/caminando por la vida.mp3"));
        cancionesRapEsp.add(new File("./resources/mala mujer.mp3"));
        cancionesRapEsp.add(new File("./resources/danger.mp3"));

        canciones.put("Pop espanol", cancionesPopEsp); // Agrega canciones Pop Español.
        canciones.put("Rap espanol", cancionesRapEsp); // Agrega canciones Rap Español.

        claves = canciones.keySet().toArray(); // Obtiene las claves de las categorías.
        Random r1 = new Random();
        claveCancion = (String) claves[r1.nextInt(claves.length)]; // Selecciona una categoría aleatoria.
    }

    public void determinarModoDeJuego(List<Integer> lista) {
        int contador = 0;

        if (lista.size() == 4) { // Verifica que se recibieron los votos de los 4 jugadores.
            for (int valor : lista) {
                if (valor == 1) {
                    contador++; // Cuenta los votos para el modo 1.
                }
            }
            System.out.println(contador);
            aniadirCanciones(); // Añade canciones antes de iniciar el modo de juego.
            if (contador == 2 || contador > 2) {
                modoJuego1 = true;
                modoDeJuego1(); // Inicia el modo 1 si hay suficientes votos.
            } else {
                modoDeJuego2(); // Inicia el modo 2 en caso contrario.
            }
        } else {
            System.out.println("Algo ha fallado"); // Mensaje en caso de fallo.
        }
    }


    public void empezarVotacion(ArrayList<Jugador> jugadores) {
        CountDownLatch vota = new CountDownLatch(4); // Sincroniza los votos de 4 jugadores.
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        List<Future<Integer>> futures = new ArrayList<>();

        // Crea tareas para manejar las votaciones de cada jugador.
        List<ClienteHandler> handlers = List.of(
            new ClienteHandler(vota, jugadores.get(0)),
            new ClienteHandler(vota, jugadores.get(1)),
            new ClienteHandler(vota, jugadores.get(2)),
            new ClienteHandler(vota, jugadores.get(3))
        );

        for (ClienteHandler handler : handlers) {
            futures.add(executorService.submit(handler)); // Ejecuta las tareas en el pool de hilos.
        }

        ArrayList<Integer> listaVotaciones = new ArrayList<>();

        try {
            for (Future<Integer> future : futures) {
                listaVotaciones.add(future.get()); // Recoge los resultados de las votaciones.
            }
            determinarModoDeJuego(listaVotaciones); // Determina el modo de juego según los votos.
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executorService.shutdown(); // Libera recursos del pool de hilos.
        }
    }

    public void modoDeJuego1() {
        List<File> cancionesElegidas = canciones.get(claveCancion); // Obtiene las canciones de la categoría elegida.
        System.out.println("Comenzando nueva partida con " + jugadores.size() + " jugadores.");
        System.out.println("Se han elegido canciones de la temática: " + claveCancion);

        try {
            for (PrintWriter out : outs) {
                // Notifica a los jugadores la categoría de canciones.
                out.println("Tema de las canciones: " + claveCancion);
                out.flush();
            }

            boolean isLastSong = false;
            for (int i = 0; i < cancionesElegidas.size(); i++) {
                if (i == cancionesElegidas.size() - 1) {
                    isLastSong = true; // Marca si es la última canción.
                }

                CountDownLatch cd = new CountDownLatch(jugadores.size());
                System.out.println("Empieza el turno " + turno + " con la canción: " + cancionesElegidas.get(i).getName());

                for (int j = 0; j < jugadores.size(); j++) {
                    // Crea un hilo para procesar el turno de cada jugador.
                    MiHilo miHilo = new MiHilo(cancionesElegidas.get(i), isLastSong, j, cd, jugadores);
                    pool.execute(miHilo);
                }

                cd.await(); // Espera a que todos los jugadores completen su turno.
                turno++;
                Thread.sleep(2000); // Pausa entre turnos.
            }

            System.out.println("Todos los turnos han acabado.");
            for (PrintWriter out : outs) {
                out.println("FIN"); // Notifica el final de la partida.
            }

            for (Jugador jugador : jugadores) {
                for (PrintWriter out : outs) {
                    out.println(jugador.toString()); // Envía las puntuaciones de los jugadores.
                }
            }
            guardarPuntuacionesEnXML("prueba.xml");

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                // Cierra todos los recursos de red.
                for (int i = 0; i < outs.size(); i++) {
                    outs.get(i).close();
                    ins.get(i).close();
                    sockets.get(i).close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    
    private void modoDeJuego2() {
        // Obtener la lista de canciones para el tema seleccionado
        List<File> cancionesElegidas = canciones.get(claveCancion);
        System.out.println("Comenzando Modo de Juego 2: ¡El más rápido gana! Tema: " + claveCancion);

        try {
            // Informar a los jugadores sobre el modo de juego actual
            for (PrintWriter out : outs) {
                out.println("Modo de Juego 2: ¡El más rápido gana! Tema: " + claveCancion);
                out.flush();
            }

            // Iterar sobre las canciones seleccionadas
            for (int i = 0; i < cancionesElegidas.size(); i++) {
                boolean isLastSong = (i == cancionesElegidas.size() - 1); // Determina si es la última canción
                File cancionActual = cancionesElegidas.get(i);
                cancionAcertada = false;

                System.out.println("Empieza el turno " + turno);

                // Contador para coordinar a los jugadores
                CountDownLatch cd = new CountDownLatch(4);

                // Lanzar un hilo por cada jugador
                for (int j = 0; j < jugadores.size(); j++) {
                    MiHiloRapido miHilo = new MiHiloRapido(cancionActual, isLastSong, j, cd, jugadores);
                    pool.execute(miHilo);
                }

                // Esperar a que alguien acierte o a que todos terminen
                cd.await();

                // Esperar antes de iniciar el siguiente turno
                Thread.sleep(2000);

                turno++; // Incrementar el turno
            }

            System.out.println("Todos los turnos han acabado.");
            
            // Preparar resultados finales
            StringBuilder resultados = new StringBuilder("\nLa partida ha acabado, con las siguientes puntuaciones:\n");
            for (PrintWriter out : outs) {
                out.println("FIN");
            }

            // Enviar las puntuaciones a todos los jugadores
            for (Jugador jugador : jugadores) {
                for (PrintWriter out : outs) {
                    out.println(jugador.toString());
                }
            }

            System.out.println(resultados);
            guardarPuntuacionesEnXML("prueba.xml"); // Guardar puntuaciones en un archivo XML

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                // Cerrar recursos al finalizar
                for (int i = 0; i < outs.size(); i++) {
                    outs.get(i).close();
                    ins.get(i).close();
                    sockets.get(i).close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        System.out.println("La partida va a comenzar. Solicitando nombres de los jugadores...");

        // Contador para sincronizar la obtención de nombres
        CountDownLatch latch = new CountDownLatch(jugadores.size());
        ExecutorService executor = Executors.newFixedThreadPool(jugadores.size());

        // Lanzar tareas para solicitar nombres de los jugadores
        for (Jugador jugador : jugadores) {
            executor.execute(() -> solicitarNombreJugador(jugador, latch));
        }

        try {
            // Esperar a que todos los jugadores hayan proporcionado sus nombres
            latch.await();
            System.out.println("Todos los jugadores han proporcionado sus nombres: " + jugadores);
            
            // Iniciar la votación después de que se hayan registrado todos los nombres
            empezarVotacion(jugadores);
            
            // Mostrar información de los jugadores
            for (Jugador jugador : jugadores) {
                System.out.println(jugador);
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            executor.shutdown(); // Apagar el pool de hilos
        }
    }

    private void solicitarNombreJugador(Jugador jugador, CountDownLatch latch) {
        try {
            // Obtener los flujos de entrada y salida del socket del jugador
            Socket socket = jugador.getSocket();
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Pedir el nombre al cliente
            out.writeBytes("Introduce tu nombre: \n");
            String nombre = in.readLine();

            // Asignar el nombre al jugador
            jugador.setName(nombre);
            System.out.println("Jugador registrado: " + nombre);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            latch.countDown(); // Notificar que este jugador ha completado la tarea
        }
    }

    private void guardarPuntuacionesEnXML(String nombreArchivo) {
        try {
            // Crear un documento XML
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            // Elemento raíz del XML
            Element rootElement = doc.createElement("puntuaciones");
            doc.appendChild(rootElement);

            // Añadir información de cada jugador
            for (Jugador jugador : jugadores) {
                Element jugadorElement = doc.createElement("jugador");
                rootElement.appendChild(jugadorElement);

                // Añadir nombre del jugador
                Element nombreElement = doc.createElement("nombre");
                nombreElement.appendChild(doc.createTextNode(jugador.getName()));
                jugadorElement.appendChild(nombreElement);

                // Añadir puntuación del jugador
                Element puntuacionElement = doc.createElement("puntuacion");
                puntuacionElement.appendChild(doc.createTextNode(String.valueOf(jugador.getPuntuacion())));
                jugadorElement.appendChild(puntuacionElement);
            }

            // Escribir el documento XML en un archivo
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(nombreArchivo));

            transformer.transform(source, result);
            System.out.println("Las puntuaciones se han guardado en el archivo " + nombreArchivo);

        } catch (TransformerException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
    }


    static class ClienteHandler implements Callable<Integer> {
        private CountDownLatch cd;
        private Jugador j;

        public ClienteHandler(CountDownLatch cd, Jugador j) {
            this.cd = cd;
            this.j = j;
        }

        @Override
        public Integer call() {
            cd.countDown();
            try { DataInputStream in = new DataInputStream(j.getSocket().getInputStream());
            	  DataOutputStream out = new DataOutputStream(j.getSocket().getOutputStream());
            	  out.writeBytes("Elige tu opcion favorita de modo de juego\n");
                return in.readInt();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
    
  	// Clase para la ejecución concurrente de las canciones
  		class MiHilo implements Runnable {

  		    private File cancion;
  		    private boolean isLastSong;
  		    private int id;
  		    private CountDownLatch cd;
  		    private List<Jugador> jugadores;

  		    public MiHilo(File cancion, boolean isLastSong, int id, CountDownLatch cd, List<Jugador> jugadores) {
  		        this.cancion = cancion;
  		        this.isLastSong = isLastSong;
  		        this.id = id;
  		        this.cd = cd;
  		        this.jugadores = jugadores;
  		    }

  		    @Override
  		    public void run() {
  		        try {
  		            PrintWriter out = outs.get(id);
  		            BufferedReader in = ins.get(id);

  		            String cancionNombre = cancion.getName().substring(0, cancion.getName().lastIndexOf("."));
  		            out.println("Turno " + turno + ": Intenta adivinar la canción.");
  		            out.flush();
  	                enviarCancion(cancion, sockets.get(id).getOutputStream());
  		            String respuesta = in.readLine();

  		            if (respuesta != null && respuesta.equalsIgnoreCase(cancionNombre)) {
  		                int puntos = calcularPuntos(cd.getCount());
  		                jugadores.get(id).sumarPuntuacion(puntos);
  		                out.println("¡Correcto! Has ganado " + puntos + " puntos.");
  		                System.out.println("Jugador " + jugadores.get(id).getName() + " ha acertado y ganado " + puntos + " puntos.");
  		            } else {
  		                out.println("Respuesta incorrecta. Intenta en el siguiente turno.");
  		                System.out.println("Jugador " + jugadores.get(id).getName() + " ha fallado.");
  		            }

  		            if (isLastSong) {
  		            	
  		                out.println(" Esta era la última canción.");
  		            } else {
  		                out.println("Espera al siguiente turno...");
  		            }
  		            out.flush();

  		        } catch (IOException e) {
  		            e.printStackTrace();
  		        } finally {
  		            cd.countDown(); 
  		        }
  		    }

  		    private int calcularPuntos(long jugadoresRestantes) {
  		     
  		        if (jugadoresRestantes == 4) return 20; 
  		        if (jugadoresRestantes == 3) return 10;
  		        if (jugadoresRestantes == 2) return 5;  
  		        return 1;                                
  		    }
  		}
  		
  		 private void enviarCancion(File cancion, OutputStream outputStream) throws IOException {
  	        try (FileInputStream fis = new FileInputStream(cancion)) {DataOutputStream dataOut = new DataOutputStream(outputStream);
  	            long fileSize = cancion.length();
  	            dataOut.writeLong(fileSize);
  	            dataOut.flush();

  	            byte[] buffer = new byte[4096];
  	            int bytesRead;
  	            while ((bytesRead = fis.read(buffer)) != -1) {
  	                dataOut.write(buffer, 0, bytesRead);
  	            }
  	            dataOut.flush();
  	            System.out.println("Canción enviada: " + cancion.getName());
  	        }
  	        catch (IOException e) {
				e.printStackTrace();
			}
  	    }
  		 
  		class MiHiloRapido implements Runnable {

  		    private File cancion;
  		    private boolean isLastSong;
  		    private int id;
  		    private CountDownLatch cd;
  		    private List<Jugador> jugadores;

  		    public MiHiloRapido(File cancion, boolean isLastSong, int id, CountDownLatch cd, List<Jugador> jugadores) {
  		        this.cancion = cancion;
  		        this.isLastSong = isLastSong;
  		        this.id = id;
  		        this.cd = cd;
  		        this.jugadores = jugadores;
  		    }

  		    @Override
  		    public void run() {
  		        try {
  		            PrintWriter out = outs.get(id);
  		            BufferedReader in = ins.get(id);
  		            Boolean acertado = false;
  		            String cancionNombre = cancion.getName().substring(0, cancion.getName().lastIndexOf("."));
  		            out.println("Turno " + turno + ": Intenta adivinar la canci�n.");
  		            out.flush();

  		            enviarCancion(cancion, sockets.get(id).getOutputStream());

  		            Instant inicio = Instant.now();

  		            while (!cancionAcertada) {
  		            	
  		         
  		                String respuesta = in.readLine();
  		            	
  		            	
  		            	
  		                Instant fin = Instant.now();
  		                long tiempo = Duration.between(inicio, fin).toMillis();

  		                synchronized (this) {
  		                    if (respuesta != null && respuesta.equalsIgnoreCase(cancionNombre)) {
  		                        if (!cancionAcertada) { // El primero en acertar detiene todo
  		                            cancionAcertada = true;
  		                            jugadores.get(id).sumarPuntuacion(10);
  		                            out.println("Correcto! Has sido el más rápido y has ganado 10 puntos en " + tiempo + " ms.");
  		                            System.out.println("Jugador " + jugadores.get(id).getName() + " ha acertado en " + tiempo + " ms.");

  		                            // Notificar a los dem�s jugadores
  		                          for (PrintWriter o : outs) {
	                                    o.println("La canción ha sido acertada por " + jugadores.get(id).getName() + "!");
	                                    o.flush();
  		                        }
  		                                
  		                            
  		                        } else {
  		                            out.println("Ya hay un ganador! Espera el siguiente turno.");
  		                        }
  		                      
  		                    }
  		                    else {
  		                    	 out.println("Te has equivocado de cancion.");
  		                    	
  		                    }
  		                }
  		            }

  		            if (isLastSong) {
  		                out.println(" Esta era la última canción.");
  		              
  		            } else {
  		                out.println("Espera al siguiente turno...");
  		            }
  		            out.flush();

  		        } catch (IOException e) {
  		            e.printStackTrace();
  		        } finally {
  		            cd.countDown(); // Reducir el contador independientemente de si acertó o no
  		        }
  		    }
  		}

  		
  		}
  	