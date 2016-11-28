package player;

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

import utils.HTTPUtilities;

public class FilePlayer {

    public static void main(String[] args) throws Exception {

        Map<String, List<Segment>> dataMap = new HashMap<>();
        List<Segment> buffer = new ArrayList<>();
        Queue<Segment> toPlay = new ConcurrentLinkedDeque<>();

        List<Double> averageSpeeds = new ArrayList<>();

        double averageSpeed = 0;

        Thread playerIng = new Thread(new Runnable() {
            public void run() {

                Player player = JavaFXMediaPlayer.getInstance().setSize(800, 500).mute(false);

                while (!toPlay.isEmpty()) {

                    Segment tmp = toPlay.poll();
                    System.err.println("Data: " + tmp.getNumber() + " = " + tmp.getData());

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

        readIndex(dataMap);
        int [] indexSegment = new int[dataMap.size()];

        fillIndex(dataMap, indexSegment);

        String urlIndex = "http://localhost:8080";
        URL url = new URL(urlIndex);

        InetAddress serverAddr = InetAddress.getByName(url.getHost());

        int k = 0;
        List<Segment> lst = dataMap.get(indexSegment[k]+".ts");
        int i = 0;
        boolean check = false;

        while (i < lst.size() - 1) {
            
            if(averageSpeed+200 > indexSegment[k] && k< indexSegment.length){
                k++;
                lst = dataMap.get(indexSegment[k]+".ts");
                
            }
            if(averageSpeed < indexSegment[k] && k>0){
                k--;
                 lst = dataMap.get(indexSegment[k]+".ts");
            }
            
            
            Socket sock = new Socket(serverAddr, 8080);

            OutputStream toServer = sock.getOutputStream();
            InputStream fromServer = sock.getInputStream();

            DataInputStream dis = new DataInputStream(fromServer);
            System.out.println("Connected to server");

            Segment seg = lst.get(i);

            int initialRange = seg.getOffset();
            int finalRange = (lst.get(i + 1).getOffset() - 1);

            String request = String.format("GET %s HTTP/1.0\r\n" + "User-Agent: X-RC2016\r\n"
                    + "Range: bytes=%d-%d\r\n\r\n", "/finding-dory/" + indexSegment[k]+".ts", initialRange, finalRange);

            toServer.write(request.getBytes());
            long sTime = System.currentTimeMillis();
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
                long rTime = System.currentTimeMillis();

                double dataSize = ((finalRange - initialRange) / 1024) * 8;
                averageSpeed = calcRTT(averageSpeeds, rTime, sTime, dataSize);
                System.err.println("Average Download Speed: " + averageSpeed);

                seg.addData(data);

                buffer.add(seg);
                toPlay.add(seg);

                if (toPlay.size() == 5 && !check) {
                    playerIng.start();
                    check = true;
                }

                dis.close();
                sock.close();
            } catch (EOFException e) {
            }

            i++;
        }

    }

    private static void readIndex(Map<String, List<Segment>> dataMap) throws Exception {
        String urlIndex = "http://localhost:8080";
        URL url = new URL(urlIndex);

        InetAddress serverAddr = InetAddress.getByName(url.getHost());

        Socket sock = new Socket(serverAddr, 8080);

        OutputStream toServer = sock.getOutputStream();
        InputStream fromServer = sock.getInputStream();
        System.out.println("Connected to server");

        String request = String.format("GET %s HTTP/1.0\r\n" + "User-Agent: X-RC2016\r\n\r\n", "/finding-dory/index.dat");

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

    public static double calcRTT(List<Double> lst, double rTime, double sTime, double dataSize) {
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

    private static void fillIndex(Map<String, List<Segment>> dataMap, int[] indexSegment) {
        int i = 0;
        for (String s : dataMap.keySet()) {
            indexSegment[i] = Integer.parseInt(s.split("\\.")[0]);
            i++;
        }
        Arrays.sort(indexSegment);
    }

}
