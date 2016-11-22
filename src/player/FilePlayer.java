package player;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.*;

public class FilePlayer {

	public static void main(String[] args) throws Exception {
	    String fileName = args.length != 1 ? "finding-dory/128.ts" : args[0];
	    Player player = JavaFXMediaPlayer.getInstance().setSize(800, 460).mute(false);
		FileInputStream fis = new FileInputStream(fileName);
		DataInputStream dis = new DataInputStream(fis);

		while (!dis.readUTF().equals("eof")) {
			dis.readLong(); 
			dis.readLong(); 
			byte[] data = new byte[dis.readInt()];
			dis.readFully(data);
			player.decode(data);
		}
		dis.close();
	}

}
