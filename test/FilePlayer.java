/*
 * Trabalho Prático 2 - Redes de Computadores 2015/2016
 * Miguel Afonso Madeira nº43832
 * Luís Correia nº42832
 * 
 */
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import player.JavaFXMediaPlayer;
import player.Player;
import utils.HTTPUtilities;

public class FilePlayer {

    private static OutputStream toServer;
    private static InputStream fromServer;
    private static Socket sock;
    private static List<Segment> list;
    private static int next;

    private static int[] files;
    private static int[] filesDownloaded;

    private static TreeMap<String, List<Segment>> tabela = new TreeMap<String, List<Segment>>();
    private static String server;
    private static int playoutDelay;

    private static final String INDEX_FILE = "index.dat";

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            System.out.println("\nError, please use: player file.ts\n(to play the video file.ts encoded with a certain resolution)\n");
            System.exit(0);
        }

        /*É só desconmentar e comentar a seguir para usar o FXMediaPlayer ou o VLC
		 * 		-em ambiente Windows o FXmediaPlayer não reproduz os primeiros segmentos por alguma razao
		 * 	no vlc, não existe este problema
         */
        Player player = JavaFXMediaPlayer.getInstance().setSize(1280, 700).mute(false);
        //Player player = VlcMediaPlayer.getInstance().setSize(700, 80);

        server = args[0];
        playoutDelay = Integer.parseInt(args[1]);

        decodeIndexData();

        //Inicializações iniciais
        ConcurrentLinkedDeque<byte[]> buffers = new ConcurrentLinkedDeque<byte[]>();
        List<Double> averageSpeed = new ArrayList<Double>();
        list = tabela.get(files[0] + ".ts");
        double downloadSpeed = files[0];
        long timeDownloaded = 0;
        String fileName = "";
        int file = closestFromList(downloadSpeed);

        //Ciclo que indica a quantos blocos corresponde o playoutDelay em segundos
        int aux = 0;
        int total = 0;
        for (int i = 0; i < list.size(); i++) {
            total += list.get(i).duration;
            if (total > playoutDelay * 1000) {
                aux = i;
                break;
            }
        }

        int initialDelay = aux;

        //Thread que trata sempre do envio dos segmentos na lista, para o player
        new Thread(() -> {
            try {
                while (buffers.size() < initialDelay) 
					; //nao sai daqui ate o tamanho do buffers for maior que o initialDelay
                while (true) {
                    while (buffers.size() > 0) {
                        player.decode(buffers.removeFirst());
                    }
                }
            } catch (Exception e) {
            }
        }).start();

        //Ciclo que decide quantos ficheiro são enviados de uma vez, o valor por defeito deveria ser 5
        //porem ,se o initial delay for menor, este valor tambem sera
        int oldNext = 0;
        if (5 > initialDelay) {
            next = initialDelay;
            oldNext = initialDelay;
        } else {
            next = 5;
            oldNext = 5;
        }

        //Ciclo que envia o ficheiro na sua totalidade, segmento a segmento
        for (int i = 0; i <= list.size() - 1;) {
            int lastFile = file;

            //Um pequeno boost
            if (downloadSpeed > 1101 && downloadSpeed < 1201) {
                file = closestFromList(downloadSpeed + 100);
            } else {
                file = closestFromList(downloadSpeed + 20);
            }

            double begin = System.currentTimeMillis();

            //Para ver se é necessario abrir uma socket nova ou nao
            if (i > 0 && lastFile != file) {
                sock.close();
                fileName = openSocket(file);
            }
            if (i == 0) {
                fileName = openSocket(file);
            }

            // Para os ultimos segmentos
            if (i + oldNext > list.size() - 1) {
                next = list.size() - (i + 1);
                if (file > 0) {
                    file--;
                }
            }

            //Só para fazer prints no final, para verificar quantas vezes é sacado certo segmento
            filesDownloaded[file] += next;

            Segment currentSegment = list.get(i);
            Segment nextSegment = list.get(i + next);

            // Now we will make and send a request (GET)
            String request
                    = String.format("GET %s HTTP/1.1\r\nRange: bytes=%d-%d\r\nUser-Agent: X-513cefec7aefc4adadcd6b7595215f94-IgnorarIsto\r\n\r\n", fileName, currentSegment.offset, nextSegment.offset);

            toServer.write(request.getBytes());
            System.out.println("Sent request: " + request);
            String answerLine = HTTPUtilities.readLine(fromServer);

            // Now we will receive the reply
            System.out.println("----------------------------------------");
            System.out.println("The header of reply ...");
            System.out.println("----------------------------------------");
            System.out.println("Got answer: " + answerLine);

            String[] result = HTTPUtilities.parseHttpRequest(answerLine);
            if (result[1].equalsIgnoreCase("206")) {
                System.out.println("The file exists, answer was: " + answerLine);

                System.out.println("------------------------------------------------");
                while (!answerLine.equals("")) {
                    System.out.println("Got:\t" + answerLine);
                    answerLine = HTTPUtilities.readLine(fromServer);
                }

                System.out.println("----------------------------------------");
                System.out.println("The body of reply ...");
                System.out.println("----------------------------------------");

                DataInputStream dis = new DataInputStream(fromServer);

                for (int j = 0; j < next; j++) {
                    dis.readUTF();
                    dis.readLong();
                    dis.readLong();

                    //Buffer with video segment data
                    byte[] buffer = new byte[dis.readInt()];
                    dis.readFully(buffer);
                    buffers.add(buffer);
                }

                double end = System.currentTimeMillis();

                //É feita a média, apenas com os ultimos 5 conjunto de segmentos
                if (averageSpeed.size() >= 5) {
                    averageSpeed.remove(0);
                }

                //Calcula o tempo que demorou a sacar o último conjunto de segmentos
                double lastSpeed = ((((nextSegment.offset - currentSegment.offset) / 1024) * 8) / ((end - begin) / 1000));
                timeDownloaded += lastSpeed;

                //Certifica que os primeiros ficheiros são com a qualidade mais baixa.
                if (i < initialDelay) {
                    averageSpeed.add((double) files[0]);
                } else {
                    averageSpeed.add(lastSpeed);
                }

                //Calcula a média na lista
                double average = 0.0;
                for (int m = 0; m < averageSpeed.size(); m++) {
                    average += averageSpeed.get(m);
                }

                downloadSpeed = average / averageSpeed.size();

            }
            i += oldNext;
        }
        sock.close();

        System.out.println("Velocidade Média: " + (oldNext * timeDownloaded) / list.size() + " kpbs");
        for (int i = 0; i < files.length; i++) {
            System.out.println("Foram sacados " + filesDownloaded[i] + " segmentos do ficheiro " + files[i] + ".ts" + " | Percentagem:" + (filesDownloaded[i] * 100) / list.size() + "%");
        }
    }

    /*
	 * Method that creates a request to the server for the index file with the
	 * 	video information. Once the file is received, it creates a vector with
	 * 	the available files correspondent to the available bitrates.
	 * 	Furthermore, it filters the files' information and saves it to a map
	 * 	containing lists, so that a search by bitrate of a certain segment is 
	 * 	possible.
     */
    private static void decodeIndexData() throws IOException {
        int nrQualities = 0;

        String url = server + INDEX_FILE;
        URL u = new URL(url);
        System.out.println(u);

        // Assuming URL of the form http:// ....
        InetAddress serverAddr = InetAddress.getByName(u.getHost());
        int port = u.getPort();
        if (port == -1) {
            port = 80;
        }
        String fileName = u.getPath();
        Socket sock = new Socket(serverAddr, port);
        OutputStream toServer = sock.getOutputStream();
        InputStream fromServer = sock.getInputStream();
        System.out.println("Connected to server");

        // Now we will make and send a request (GET)
        String request
                = String.format("GET %s HTTP/1.0\r\nUser-Agent: X-513cefec7aefc4adadcd6b7595215f94-IgnorarIsto\r\n\r\n", fileName);

        toServer.write(request.getBytes());
        System.out.println("Sent request: " + request);
        String answerLine = HTTPUtilities.readLine(fromServer);

        String[] result = HTTPUtilities.parseHttpRequest(answerLine);
        if (result[1].equalsIgnoreCase("200") && result[2].equalsIgnoreCase("OK")) {

            while (!answerLine.equals("")) {
                System.out.println(answerLine);
                answerLine = HTTPUtilities.readLine(fromServer);
            }

            String ola = HTTPUtilities.readLine(fromServer);
            while (!ola.equals("")) {
                System.out.println(ola);
                String[] split = ola.split(" ");
                if (!split[0].equals(";")) {
                    List<Segment> auxList = new ArrayList<Segment>();
                    tabela.put(ola, auxList);
                    nrQualities++;
                }
                ola = HTTPUtilities.readLine(fromServer);
                System.out.println(ola);

            }

            ola = HTTPUtilities.readLine(fromServer);
            while (!ola.equals("")) {
                String[] split = ola.split(" ");
                if (!split[0].equals(";")) {
                    List<Segment> auxList = tabela.get(split[0]);
                    Segment dados = new Segment(Integer.parseInt(split[1]), Integer.parseInt(split[2]), Integer.parseInt(split[3]));
                    auxList.add(dados);
                    tabela.put(split[0], auxList);
                }
                ola = HTTPUtilities.readLine(fromServer);

            }

        }
        sock.close();

        System.out.println("Files retrieved: ");

        files = new int[nrQualities];
        filesDownloaded = new int[nrQualities];

        int j = 0;
        for (Entry<String, List<Segment>> entry : tabela.entrySet()) {
            String k = entry.getKey();
            ArrayList<Segment> list = (ArrayList<Segment>) entry.getValue();
            int i = 0;
            while (list.size() > i) {
                Segment seg = list.get(i);
                System.out.println(k + " " + seg.id + " " + seg.offset + " " + seg.duration);
                i++;
            }
            String[] split = k.split(".ts");
            files[j] = Integer.parseInt(split[0]);
            filesDownloaded[j++] = 0;

        }
        Arrays.sort(files);
    }

    /*
	 * Method that returns the ideal playback bitrate for a certain download speed.
     */
    public static int closestFromList(double speed) {
        System.out.println("Velocidade: " + speed);
        int min = 9999;
        int optimalResolution = 0;

        for (int i = 0; i < files.length; i++) {

            if (files[i] < speed) {
                if (files[i] > optimalResolution) {
                    optimalResolution = files[i];
                }
            }

            if (files[i] < min) {
                min = files[i];
            }
        }

        if (optimalResolution == 0) {
            optimalResolution = min;
        }

        for (int i = 0; i < files.length; i++) {
            if (files[i] == optimalResolution) {
                optimalResolution = i;
                break;
            }
        }

        return optimalResolution;
    }

    //Abre uma socket
    public static String openSocket(int file) throws IOException {
        String url;

        list = tabela.get(files[file] + ".ts");
        url = server + files[file] + ".ts";

        URL u = new URL(url);
        System.out.println(u);

        // Assuming URL of the form http:// ....
        InetAddress serverAddr = InetAddress.getByName(u.getHost());
        int port = u.getPort();
        if (port == -1) {
            port = 80;
        }
        String fileName = u.getPath();
        sock = new Socket(serverAddr, port);
        toServer = sock.getOutputStream();
        fromServer = sock.getInputStream();
        System.out.println("Connected to server");
        return fileName;
    }

    //Classe Segment, útil para alguns métodos
    static class Segment {

        private int id;
        private int offset;
        private int duration;

        public Segment(int id, int offset, int duration) {
            this.id = id;
            this.offset = offset;
            this.duration = duration;
        }
    }
}
