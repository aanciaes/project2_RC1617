package player;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import utils.HTTPUtilities;
import player.Utils;

public class JavaFXMediaPlayer extends Application implements Player {
	private static final String URL_TEMPLATE = "http://localhost:%d/index.m3u8";
	private static final int RECENT_SEGMENTS_SIZE = 20;

	private Stage stage;
	private HttpServer server;
	private MediaView mediaView;
	private MediaPlayer mediaPlayer;
	volatile private byte[] indexData;
	private static SynchronousQueue<Player> instance;
	private BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(2);
	private Label stats1 = new Label(), stats2 = new Label();

	private double q_Sums = 0, q_Samples = 0;
	private Deque<Integer> recentSegments = new ArrayDeque<>();

	@Override
	public void decode(byte[] data) {
		if (indexData == null)
			indexData = data;
		else {
			Utils.putInto(queue, data);

			Platform.runLater(() -> {
				int quality = ByteBuffer.wrap(data, data.length - Integer.BYTES, Integer.BYTES).getInt();
				recentSegments.addFirst(quality);
				while (recentSegments.size() > RECENT_SEGMENTS_SIZE)
					recentSegments.removeLast();

				q_Sums += quality;
				q_Samples += 1;
				double mean = q_Sums / q_Samples;
				stats1.setText(String.format("avg: %d kbps", (int) mean));
				stats2.setText(String.format("\n\n%s", recentSegments));
			});
		}
	}

	@Override
	public Player setSize(int width, int height) {
		stage.setWidth(width);
		stage.setHeight(height);
		return this;
	}

	@Override
	public Player mute(boolean val) {
		Utils.newThread(true, () -> {
			mediaPlayer.setMute(val);
		}).start();
		return this;
	}

	public static Player getInstance() {
		if (instance == null) {
			instance = new SynchronousQueue<>();
			Utils.newThread(true, () -> {
				launch(new String[] {});
			}).start();
		}
		return Utils.takeFrom(instance);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		stage = primaryStage;

		server = new HttpServer();

		primaryStage.setOnCloseRequest(h -> {
			System.err.println("Window closed by user...");
			System.exit(0);
		});

		server.start();

		Group root = new Group();

		Scene scene = new Scene(root, 0, 0);

		primaryStage.setTitle("MyDash Player");

		// create media player, pointing to the internal http server
		Media media = new Media(String.format(URL_TEMPLATE, server.localPort()));

		mediaPlayer = new MediaPlayer(media);
		mediaPlayer.setAutoPlay(true);

		MediaControl mediaControl = new MediaControl(mediaPlayer, scene);
		scene.setRoot(mediaControl);

		primaryStage.setScene(scene);
		primaryStage.show();

		instance.put(JavaFXMediaPlayer.this);

		// Instance is now ready...
	}

	class MediaControl extends BorderPane {
		public MediaControl(final MediaPlayer mp, Scene scene) {
			setStyle("-fx-background-color: black;");
			mediaView = new MediaView(mp);
			mediaView.autosize();
			mediaView.setFitHeight(0);

			Pane mvPane = new Pane() {
			};
			mvPane.getChildren().add(mediaView);

			stats1.setStyle("-fx-text-fill: yellow;-fx-font-size: 12pt;");
			stats2.setStyle("-fx-text-fill: green;-fx-font-size: 8pt;");

			mvPane.getChildren().add(stats1);
			mvPane.getChildren().add(stats2);
			setCenter(mvPane);
			mvPane.autosize();
			scene.widthProperty().addListener((_1, _2, width) -> {
				mediaView.setFitWidth(width.doubleValue());
			});

			scene.heightProperty().addListener((_1, _2, height) -> {
				mediaView.setFitHeight(height.doubleValue());
			});
		}
	}

	class HttpServer extends Thread {

		final ServerSocket ss;

		HttpServer() throws IOException {
			super.setDaemon(true);
			ss = new ServerSocket(0);
		}

		int localPort() {
			return ss.getLocalPort();
		}

		public void run() {
			try {
				for (;;) {
					Socket cs = ss.accept();
					handleRequest(cs);
				}
			} catch (Exception x) {
				x.printStackTrace();
			}
		}
	}

	@SuppressWarnings("unused")
	void handleRequest(Socket cs) {
		try {
			InputStream is = cs.getInputStream();
			OutputStream os = cs.getOutputStream();

			String request = HTTPUtilities.readLine(is), header;
			// System.err.println(request);
			String[] parts = HTTPUtilities.parseHttpRequest(request);

			while ((header = HTTPUtilities.readLine(is)).length() > 0) {
				// System.err.println(header);
			}

			String action = parts[0];
			String filename = parts[1];

			os.write("HTTP/1.0 200 OK\r\n".getBytes());
			if (filename.endsWith(".m3u8")) {
				os.write("Content-Type: application/x-mpegURL\r\n\r\n".getBytes());
				if (action.equals("GET")) {
					while (indexData == null)
						Thread.sleep(10);

					os.write(indexData);
				}
			} else {
				byte[] data = queue.take();
				int length = data.length - Integer.BYTES;
				os.write(String.format("Content-Length: %d\r\nContent-Type: video/MP2T\r\n\r\n", length).getBytes());
				os.write(data, 0, length);
			}
			os.close();
			cs.close();
		} catch (Exception x) {
			x.printStackTrace();
		}
	}

}
