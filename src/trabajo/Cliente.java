package trabajo;

import java.io.*;
import java.net.Socket;
import javazoom.jl.player.Player;

public class Cliente {

    // Variable compartida para detener la reproducción de una canción
    private static volatile boolean detenerReproduccion = false;
    private static boolean puedeP;

    public static void main(String[] args) {
        // Dirección del servidor al que se conectará el cliente
        String host = "127.0.0.1";
        // Puerto del servidor
        int puerto = 55556;
        PrintWriter out = null;

        try (
            // Establece una conexión al servidor
            Socket socket = new Socket(host, puerto);
            // Flujo para leer datos del servidor
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            // Flujo para recibir datos binarios del servidor
            DataInputStream dataIn = new DataInputStream(socket.getInputStream());
            // Flujo para enviar datos al servidor
            DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream())
        ) {
            System.out.println("Conectado al servidor.");
            // Flujo para enviar mensajes al servidor
            out = new PrintWriter(socket.getOutputStream(), true);
            Thread reproducirHilo = null;

            while (true) {
                // Lee un mensaje enviado por el servidor
                String mensajeServidor = in.readLine();
                if (mensajeServidor == null) break; // Salir si el servidor no envía más datos
                System.out.println(mensajeServidor);

                // Caso: El servidor solicita el nombre del jugador
                if (mensajeServidor.startsWith("Introduce tu nombre:")) {
                    String nombreJugador = leerConsola("");
                    out.println(nombreJugador); // Envía el nombre al servidor

                // Caso: Es el turno del jugador y se recibe una canción
                } else if (mensajeServidor.startsWith("Turno")) {
                    File cancionRecibida = recibirCancion(dataIn); // Recibe el archivo de canción

                    // Inicia un hilo para reproducir la canción
                    reproducirHilo = new Thread(() -> reproducirCancion(cancionRecibida));
                    reproducirHilo.start();

                    // Solicita al jugador que introduzca una respuesta
                    String respuesta = leerConsola("Introduce tu respuesta: ");
                    out.println(respuesta); // Envía la respuesta al servidor

                // Caso: El servidor solicita que el jugador elija un modo de juego
                } else if (mensajeServidor.startsWith("Elige tu opcion favorita")) {
                    System.out.println("\n\n\n\n");
                    System.out.println("----------- Bienvenido a Pista Musical -----------");
                    System.out.println("----------- Elige un modo de juego -----------");
                    System.out.println("1. Todos a la vez ");
                    System.out.println("2. El más rápido gana");
                    
                    // Lee la opción del jugador
                    String leer = leerConsola("Introduce tu opción favorita: ");
                    int numero = Integer.parseInt(leer);
                    dataOut.writeInt(numero); // Envía la opción seleccionada al servidor

                // Caso: El servidor indica que el juego ha terminado
                } else if (mensajeServidor.startsWith("FIN")) {
                    String puntuaciones = "Las puntuaciones son \n";
                    System.out.println(puntuaciones);

                    // Lee y muestra las puntuaciones finales del juego
                    for (int i = 0; i < 4; i++) { // Asume que hay 4 jugadores
                        puntuaciones = in.readLine();
                        System.out.println(puntuaciones);
                    }

                    System.out.println("Fin del juego. Cerrando conexión.");
                    break; // Termina el bucle
                }
            }
        } catch (IOException e) {
            e.printStackTrace(); // Maneja cualquier error de E/S
        }
    }

    // Método para leer la entrada del usuario desde la consola
    private static String leerConsola(String mensaje) {
        System.out.print(mensaje); // Muestra un mensaje al usuario
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            return reader.readLine(); // Devuelve la entrada del usuario
        } catch (IOException e) {
            e.printStackTrace();
            return ""; // Devuelve una cadena vacía si ocurre un error
        }
    }

    // Método para recibir un archivo de canción enviado por el servidor
    private static File recibirCancion(DataInputStream dataIn) throws IOException {
        long fileSize = dataIn.readLong(); // Tamaño del archivo enviado por el servidor
        File cancionTemporal = File.createTempFile("cancion_", ".mp3"); // Crea un archivo temporal

        try (FileOutputStream fos = new FileOutputStream(cancionTemporal)) {
            byte[] buffer = new byte[4096];
            long bytesLeidos = 0;
            int bytes;

            // Lee los datos del archivo en bloques
            while (bytesLeidos < fileSize && (bytes = dataIn.read(buffer)) != -1) {
                fos.write(buffer, 0, bytes);
                bytesLeidos += bytes;
            }
        }

        return cancionTemporal; // Devuelve el archivo recibido
    }

    // Método para reproducir una canción
    private static void reproducirCancion(File cancion) {
        try (FileInputStream fis = new FileInputStream(cancion)) {
            Player player = new Player(fis);

            // Reproduce la canción mientras no se detenga
            while (!detenerReproduccion) {
                if (!player.play(1)) { // Reproduce un bloque de la canción
                    break; // Sale si no hay más datos que reproducir
                }
            }

            player.close(); // Cierra el reproductor
            detenerReproduccion = false; // Restablece el estado de reproducción

        } catch (Exception e) {
            e.printStackTrace(); // Maneja cualquier error de reproducción
        }
    }
}
