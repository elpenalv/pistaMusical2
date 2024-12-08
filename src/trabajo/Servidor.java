package trabajo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class Servidor {
	 public static ArrayList<Jugador>  jugadores = new ArrayList<Jugador>();
	
    public static void main(String[] args) {
        try (ServerSocket soc = new ServerSocket(55556)) {
            while (true) {
                try {
    				System.out.println("Esperando a que comience una nueva partida.");
                	ExecutorService executor = Executors.newCachedThreadPool();
                	
                	for(int i = 1 ; i <=4 ; i++) {
                		Socket s = soc.accept();
                		System.out.println("Jugador número " + i + " se ha unido a la partida");

                		Jugador j = new Jugador(i, "", s);
                		jugadores.add(j);

                	}
                	
                	executor.execute(new Partida(jugadores));
                	
               System.out.println(" La Partida esta comenzando ");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    
}
