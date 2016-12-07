
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import player.JavaFXMediaPlayer;
import player.Player;
import player.Segment;

import utils.HTTPUtilities;

public class testClass {

    //Server url
    private static String server;

    //Control data structures
    private static Map<String, List<Segment>> dataMap;
    private static int[] indexSegment;

    //Queue of segments to be played
    private static Queue<Segment> toPlay;

    //Type of request to server - Number of segments requested at once
    private static int askType = 1;

    public static void download() throws Exception {
        List<Double> averageSpeeds = new ArrayList<>();
        double averageSpeed = 0;

        String urlIndex = "http://localhost:8080";
        URL url = new URL(urlIndex);

        InetAddress serverAddr = InetAddress.getByName(url.getHost());

        int optimalRes = 0;

        List<Segment> lst;
        int i = 0;
        boolean check = false;

        while (true) {
            int numSeg = 1;
            optimalRes = getOptimalResolution(averageSpeed, optimalRes, toPlay);
            int finalRange;

            lst = dataMap.get(indexSegment[optimalRes] + ".ts");

            if (i == lst.size() - 1) {
                break;
            }

            finalRange = (lst.get(i + 1).getOffset() - 1);

            if (askType == 2 && i + 5 < lst.size() - 1) {
                finalRange = (lst.get(i + 5).getOffset() - 1);
                numSeg = 5;
            }
            Socket sock = new Socket(serverAddr, 8080);

            OutputStream toServer = sock.getOutputStream();
            InputStream fromServer = sock.getInputStream();

            DataInputStream dis = new DataInputStream(fromServer);
            System.out.println("Connected to server");

            Segment seg = lst.get(i);

            int initialRange = seg.getOffset();

            String request = String.format("GET %s HTTP/1.1\r\n" + "User-Agent: X-RC2016\r\n"
                    + "Range: bytes=%d-%d\r\n\r\n", "/finding-dory/" + indexSegment[optimalRes] + ".ts", initialRange, finalRange);

            toServer.write(request.getBytes());
            double sTime = System.currentTimeMillis();
            System.out.println("Sent request: " + request);
            String answerLine = HTTPUtilities.readLine(fromServer);

            System.out.println("Got answer: " + answerLine);

            while (!answerLine.equals("")) {
                answerLine = HTTPUtilities.readLine(fromServer);
                System.out.println(answerLine);
            }

            try {

                dis.readUTF();
                dis.readLong();
                dis.readLong();
                byte[] data = new byte[dis.readInt()];

                dis.readFully(data);
                double rTime = System.currentTimeMillis();

                double dataSize = ((finalRange - initialRange) / 1024) * 8;

                averageSpeed = calcRTT(averageSpeeds, rTime, sTime, dataSize);
                System.err.println("Average Download Speed: " + averageSpeed);

                seg.addData(data);
                toPlay.add(seg);

                if (toPlay.size() == 5 && !check) {

                    check = true;
                }

                dis.close();
                sock.close();
            } catch (EOFException e) {
            }

            i += numSeg;
        }
    }

    public static void play() {
        Thread play = new Thread(new Runnable() {
            public void run() {

                Player player = JavaFXMediaPlayer.getInstance().setSize(800, 500).mute(true);
                //Player player = VlcMediaPlayer.getInstance().setSize(800, 500).mute(false);

                while (!toPlay.isEmpty()) {
                    Segment tmp = toPlay.poll();
                    player.decode(tmp.getData());

                    try {
                        Thread.sleep(tmp.getDuration());
                    } catch (InterruptedException e) {

                    }
                    if (toPlay.isEmpty()) {
                        System.err.println("Buffering...");
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {

                        }
                    }
                }
            }
        });
        play.start();
    }

    private static int getOptimalResolution(double controlSpeed, int indexPosition, Queue<Segment> toPlay) {

        if (toPlay.size() <= 3 && indexPosition > 0) {
            return indexPosition - 1;

        } else if (toPlay.size() > 10 && indexPosition < 5) {
            return indexPosition + 1;
        } else {

            //boosted 
            if (controlSpeed >= 1200) {
                askType = 2;
                if (controlSpeed >= 2000) {
                    return 4;
                } else {
                    return 3;
                }
            } //normal
            else {
                askType = 1;
                if (controlSpeed <= 255) {
                    return 0;
                } else if (controlSpeed <= 520) {
                    return 1;
                } else if (controlSpeed <= 1020) {
                    return 2;
                } else {
                    return 3;
                }

            }
        }
    }

    private static void readIndex() throws Exception {
        URL url = new URL(server);
        String path = url.getPath();

        InetAddress serverAddr = InetAddress.getByName(url.getHost());
        int port = url.getPort();

        if (port == -1) {
            port = 8080;
        }
        Socket sock = new Socket(serverAddr, port);

        OutputStream toServer = sock.getOutputStream();
        InputStream fromServer = sock.getInputStream();
        System.out.println("Connected to server");

        String request = String.format("GET %s HTTP/1.0\r\n" + "User-Agent: X-RC2016\r\n\r\n", (path + "/index.dat"));

        toServer.write(request.getBytes());
        System.out.println("Sent request: " + request);
        String answerLine = HTTPUtilities.readLine(fromServer);
        System.out.println("Got answer: " + answerLine);

        while (!answerLine.equals("")) {
            answerLine = HTTPUtilities.readLine(fromServer);
            System.out.println(answerLine);
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(fromServer));
        String content = "";

        while ((content = in.readLine()) != null) {
            if (!content.startsWith(";") && !content.equals("")) {
                if (content.endsWith(".ts")) {
                    dataMap.put(content.trim(), new ArrayList());
                } else {
                    String[] httpReplySegmented = content.split("\\s");
                    List lst = dataMap.get(httpReplySegmented[0]);
                    lst.add(new Segment(httpReplySegmented[0], Integer.parseInt(httpReplySegmented[1]),
                            Integer.parseInt(httpReplySegmented[2]), Integer.parseInt(httpReplySegmented[3])));
                    dataMap.put(httpReplySegmented[0], lst);
                }
            }
        }
    }

    private static double calcRTT(List<Double> lst, double rTime, double sTime, double dataSize) {
        double sum = 0;
        double speed = dataSize / ((rTime - sTime) / 1000);

        lst.add(speed);

        if (lst.size() > 5) {
            lst.remove(0);
        }

        for (Double l : lst) {
            sum += l;
        }

        return sum / lst.size();
    }

    private static void fillIndex() {
        int i = 0;
        for (String s : dataMap.keySet()) {
            indexSegment[i] = Integer.parseInt(s.split("\\.")[0]);
            i++;
        }
        Arrays.sort(indexSegment);
    }

    public static void main(String[] args) throws Exception {
        int playoutDelay;

        if (args.length == 0) {
            System.err.println("Error input: usage $java FilePalyer <url> <playoutdelay>");
            System.exit(0);
        }

        if (args.length == 1) {
            playoutDelay = 5;
        } else {
            playoutDelay = Integer.parseInt(args[1]);
        }

        server = args[0];
        dataMap = new HashMap<>();
        toPlay = new ConcurrentLinkedDeque<>();

        readIndex();
        indexSegment = new int[dataMap.size()];
        fillIndex();

        download();
        
        play();
    }

}
