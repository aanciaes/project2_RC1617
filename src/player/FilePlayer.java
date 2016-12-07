package player;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
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

import utils.HTTPUtilities;

public class FilePlayer {

    //Server url and path
    private static URL server;
    private static String urlPath;

    //Control data structures
    private static Map<String, List<Segment>> dataMap;
    private static int[] indexSegment;

    //Queue of segments to be played
    private static Queue<Segment> toPlay;

    //Num of segments to be requested to server at once
    private static int numSegments = 3;

    public static void download() throws Exception {
        List<Double> averageSpeeds = new ArrayList<>();
        double averageSpeed = 0;

        InetAddress serverAddr = InetAddress.getByName(server.getHost());

        int optimalRes = 0;

        List<Segment> lst;
        int i = 0;

        Socket sock = new Socket(serverAddr, server.getPort());

        OutputStream toServer = sock.getOutputStream();
        InputStream fromServer = sock.getInputStream();
        DataInputStream dis = new DataInputStream(fromServer);
        System.out.println("Connected to server");

        while (true) {
            int segs = numSegments;
            optimalRes = getOptimalResolution(averageSpeed);
            lst = dataMap.get(indexSegment[optimalRes] + ".ts");

            if (i >= lst.size() - 1) {
                break;
            } else {
                if (i + segs >= lst.size() - 1) {
                    segs = lst.size() - (i + 1);
                }
            }

            int initialRange = lst.get(i).getOffset();
            int finalRange = (lst.get(i + segs).getOffset());

            String request = String.format("GET %s HTTP/1.1\r\n" + "User-Agent: X-RC2016\r\n"
                    + "Range: bytes=%d-%d\r\n\r\n", urlPath + indexSegment[optimalRes] + ".ts", initialRange, finalRange);

            toServer.write(request.getBytes());
            double sTime = System.currentTimeMillis();
            System.out.println("Sent request: " + request);
            String answerLine = HTTPUtilities.readLine(fromServer);
            System.out.println("Got answer: " + answerLine);

            int httpCode = interpretHeaders(answerLine, fromServer);
            if (httpCode != 206) {
                System.err.println("Something went wrong. Http Error Code: " + httpCode);
                System.exit(0);
            }

            try {
                for (int j = 0; j < segs; j++) {
                    Segment seg = lst.get(i + j);

                    dis.readUTF();
                    dis.readLong();
                    dis.readLong();
                    byte[] data = new byte[dis.readInt()];
                    dis.readFully(data);

                    seg.addData(data);
                    toPlay.add(seg);
                }
            } catch (EOFException e) {
                e.printStackTrace();
            }

            //Speeds
            double rTime = System.currentTimeMillis();
            double dataSize = ((finalRange - initialRange) / 1024) * 8;
            averageSpeed = calcRTT(averageSpeeds, rTime, sTime, dataSize);
            System.err.println("Average Download Speed: " + averageSpeed);

            i += segs;
        }
        dis.close();
        sock.close();
    }

    public static void play(int playoutDelay) {
        Thread play = new Thread(new Runnable() {
            public void run() {
                //Sleeps the playout delay
                try {
                    Thread.sleep(playoutDelay * 1000);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }

                Player player = JavaFXMediaPlayer.getInstance().setSize(800, 500).mute(false);
                //Player player = VlcMediaPlayer.getInstance().setSize(800, 500).mute(false);
                while (true) {
                    while (!toPlay.isEmpty()) {
                        Segment tmp = toPlay.poll();
                        player.decode(tmp.getData());

                        try {
                            Thread.sleep(tmp.getDuration());
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }
        });
        play.start();
    }

    private static int getOptimalResolution(double controlSpeed) {
        int optimal = 0;

        for (int j = 0; j < indexSegment.length; j++) {
            if (controlSpeed > indexSegment[j]) {
                optimal = j;
            }
        }

        return 4;
    }

    private static void readIndex() throws Exception {
        InetAddress serverAddr = InetAddress.getByName(server.getHost());
        int port = server.getPort();

        if (port == -1) {
            port = 8080;
        }
        Socket sock = new Socket(serverAddr, port);

        OutputStream toServer = sock.getOutputStream();
        InputStream fromServer = sock.getInputStream();
        System.out.println("Connected to server");

        String request = String.format("GET %s HTTP/1.0\r\n" + "User-Agent: X-RC2016\r\n\r\n", (urlPath + "index.dat"));

        toServer.write(request.getBytes());
        System.out.println("Sent request: " + request);
        String answerLine = HTTPUtilities.readLine(fromServer);
        System.out.println("Got answer: " + answerLine);

        int httpCode = interpretHeaders(answerLine, fromServer);
        if (httpCode != 200) {
            System.err.println("Something went wrong. Http Error Code: " + httpCode);
            System.exit(0);
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

        if (lst.size() > 3) {
            lst.remove(0);
        }

        for (Double l : lst) {
            sum += l;
        }

        return sum / lst.size();
    }

    private static int getCurrentTimeinQueue() {
        int total = 0;

        for (Segment seg : toPlay) {
            total += seg.getDuration();
        }

        //return time in seconds
        return (total / 1000);
    }

    private static void fillIndex() {
        int i = 0;
        for (String s : dataMap.keySet()) {
            indexSegment[i] = Integer.parseInt(s.split("\\.")[0]);
            i++;
        }
        Arrays.sort(indexSegment);
    }

    private static int interpretHeaders(String answer, InputStream fromServer) throws IOException {
        String[] header = HTTPUtilities.parseHttpRequest(answer);
        int httpCode = Integer.parseInt(header[1]);

        while (!answer.equals("")) {
            answer = HTTPUtilities.readLine(fromServer);
            System.out.println(answer);
        }
        return httpCode;
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

        server = new URL(args[0]);
        urlPath = server.getPath() + "/";

        dataMap = new HashMap<>();
        toPlay = new ConcurrentLinkedDeque<>();

        readIndex();
        indexSegment = new int[dataMap.size()];
        fillIndex();

        play(playoutDelay);
        download();
    }
}
