package trabajo;


import java.io.Serializable;
import java.net.Socket;

public class Jugador implements Serializable  {

	private static final long serialVersionUID = 1L;
	
	private int id ;
	private String name;
	private int puntuacion = 0;
	private int turno;
	private Socket s;

	
	
	public Jugador(int id, String name, Socket s) {
		
		this.id = id;
		this.name = name;
		this.s = s;

	}
	
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public int getPuntuacion() {
		return puntuacion;
	}
	public void sumarPuntuacion(int puntuacion) {
		this.puntuacion += puntuacion;
	}
	public int getTurno() {
		return turno;
	}
	public void setTurno(int turno) {
		this.turno = turno;
	}
	
	public String toString() {
		return getName() + ". Puntuacion: " + this.getPuntuacion();
		
	}
	
	public Socket getSocket() {
		
		return this.s;
	}

	
	
}
