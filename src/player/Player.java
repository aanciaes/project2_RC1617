package player;

public interface Player {

	Player setSize(int width, int height);

	Player mute(boolean flag);

	void decode(byte[] data);

}
