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

    /**
     * Method that starts downloading the files Performs http requests to server
     * over http 1.1 using one open socket during all download time
     *
     * @throws Exception
     */
    public static void download() throws Exception {
        List<Double> averageSpeeds = new ArrayList<>(); //List of last download speeds of segments
        double averageSpeed = 0;
        int optimalRes = 0; //Resolution to download
        List<Segment> lst;
        int i = 0;

        //Open socket and creating reading and downloading mechanisms
        InetAddress serverAddr = InetAddress.getByName(server.getHost());
        Socket sock = new Socket(serverAddr, server.getPort());
        
        OutputStream toServer = sock.getOutputStream();
        InputStream fromServer = sock.getInputStream();
        DataInputStream dis = new DataInputStream(fromServer);
        System.out.println("Connected to server");
        //

        while (true) {
            int segs = numSegments;
            optimalRes = getOptimalResolution(averageSpeed);
            lst = dataMap.get(indexSegment[optimalRes] + ".ts");

            //Stop while(true) cicle when there's no more segments to download
            if (i >= lst.size() - 1) {
                break;
            } else {
                if (i + segs >= lst.size() - 1) { //Helps download last segments
                    segs = lst.size() - (i + 1);
                }
            }

            //Range of bites to request to server
            int initialRange = lst.get(i).getOffset();
            int finalRange = (lst.get(i + segs).getOffset());
            
            String request = String.format("GET %s HTTP/1.1\r\n" + "User-Agent: X-RC2016\r\n"
                    + "Range: bytes=%d-%d\r\n\r\n", urlPath + indexSegment[optimalRes] + ".ts", initialRange, finalRange);
            
            toServer.write(request.getBytes()); //Send Request to server
            double sTime = System.currentTimeMillis(); //stats
            System.out.println("Sent request: " + request);

            //Reading and analysing response headers
            String answerLine = HTTPUtilities.readLine(fromServer);
            System.out.println("Got answer: " + answerLine);
            
            int httpCode = interpretHeaders(answerLine, fromServer);
            if (httpCode != 206) {
                System.err.println("Something went wrong. Http Error Code: " + httpCode);
                System.exit(0);
            }
            //

            //Acctual downloading of movie data in http response 
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
            }//

            //Speeds
            double rTime = System.currentTimeMillis();
            double dataSize = ((finalRange - initialRange) / 1024) * 8;
            averageSpeed = calcAverageSpeed(averageSpeeds, rTime, sTime, dataSize);
            System.err.println("Average Download Speed: " + averageSpeed);
            
            i += segs;
        }
        //Closing structures when finished
        dis.close();
        sock.close();
        //
    }

    /**
     * Starts a thread that will play the video while the same itś downloading
     *
     * @param playoutDelay The movie will not start while there's palyoutDelay
     * seconds already downloaded
     */
    public static void play(int playoutDelay) {
        Thread play = new Thread(new Runnable() {
            public void run() {
                //Sleeps the playout delay
                while (getCurrentTimeinQueue() < playoutDelay)
                    ;
                
                Player player = JavaFXMediaPlayer.getInstance().setSize(800, 500).mute(false);
                //Player player = VlcMediaPlayer.getInstance().setSize(800, 500).mute(false);

                while (true) {
                    while (!toPlay.isEmpty()) {
                        Segment tmp = toPlay.poll();
                        player.decode(tmp.getData());

                        //Sleeps the amount needed to play the segment
                        //Ensures that player queue doesnt get overloaded 
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

    /**
     * Algorithm that calculates the optimal resolution of the next segment to
     * download
     *
     * @param controlSpeed Download speed of last segments
     * @return
     */
    private static int getOptimalResolution(double controlSpeed) {
        int optimal = 0;
        
        int time = getCurrentTimeinQueue();
        if (time == 0) {
            return 0;
        }
        for (int j = indexSegment.length - 1; j > -1; j--) {
            
            int aux = indexSegment[j] / time;
            if (aux * 1.5 < (controlSpeed * 0.4)) {
                optimal = j;
                numSegments = 3;
                break;
            }
        }
        
        if (time < 5) {
            if (optimal - 1 >= 0) {
                optimal--;
                numSegments = 1;
            }
            if (time < 3) {
                if (optimal - 1 >= 0) {
                    optimal--;
                    numSegments = 1;
                }
            }
        }
        
        return optimal;
    }

    /**
     * Reads the index.dat file to a control structure to be used during
     * downloading thread
     *
     * @throws Exception
     */
    private static void readIndex() throws Exception {

        //Connects to server -Open socket and creating reading and downloading mechanisms
        InetAddress serverAddr = InetAddress.getByName(server.getHost());
        int port = server.getPort();
        
        if (port == -1) {
            port = 8080;
        }
        Socket sock = new Socket(serverAddr, port);
        
        OutputStream toServer = sock.getOutputStream();
        InputStream fromServer = sock.getInputStream();
        System.out.println("Connected to server");
        //
        
        String request = String.format("GET %s HTTP/1.0\r\n" + "User-Agent: X-RC2016\r\n\r\n", (urlPath + "index.dat"));
        
        toServer.write(request.getBytes()); //Sends request to server
        System.out.println("Sent request: " + request);
        String answerLine = HTTPUtilities.readLine(fromServer);
        System.out.println("Got answer: " + answerLine);

        //Reading and analysing response headers
        int httpCode = interpretHeaders(answerLine, fromServer);
        if (httpCode != 200) {
            System.err.println("Something went wrong. Http Error Code: " + httpCode);
            System.exit(0);
        }
        //

        //Structure to read index.dat
        BufferedReader in = new BufferedReader(new InputStreamReader(fromServer));
        String content = "";
        
        while ((content = in.readLine()) != null) {
            if (!content.startsWith(";") && !content.equals("")) {  //Ignore Commnets and blanklines
                //Detects all available qualities
                if (content.endsWith(".ts")) {
                    dataMap.put(content.trim(), new ArrayList());
                } else {
                    //Reads all segments and their configuration and stores it
                    String[] httpReplySegmented = content.split("\\s");
                    List lst = dataMap.get(httpReplySegmented[0]);
                    lst.add(new Segment(httpReplySegmented[0], Integer.parseInt(httpReplySegmented[1]),
                            Integer.parseInt(httpReplySegmented[2]), Integer.parseInt(httpReplySegmented[3])));
                    dataMap.put(httpReplySegmented[0], lst);
                }
            }
        }
    }

    /**
     * Calculate the average downloading speed of the last 3 segments that were
     * downloaded
     *
     * @param lst List containing last downloading speeds
     * @param rTime Response time
     * @param sTime Request Time
     * @param dataSize Size of data
     * @return The average speed
     */
    private static double calcAverageSpeed(List<Double> lst, double rTime, double sTime, double dataSize) {
        double sum = 0;
        double speed = dataSize / ((rTime - sTime) / 1000);
        
        lst.add(speed);

        //Last three segments - Maintains list with no more than three elements
        if (lst.size() > 3) {
            lst.remove(0);
        }
        
        for (Double l : lst) {
            sum += l;
        }
        
        return sum / lst.size();
    }

    /**
     * Computes the current time of segments already download but not streamed
     * yet
     *
     * @return Time in seconds
     */
    private static int getCurrentTimeinQueue() {
        int total = 0;
        
        for (Segment seg : toPlay) {
            total += seg.getDuration();
        }

        //return time in seconds
        return (total / 1000);
    }

    /**
     * Fills the indexes array with all qualities available on Integer format
     */
    private static void fillIndex() {
        int i = 0;
        for (String s : dataMap.keySet()) {
            indexSegment[i] = Integer.parseInt(s.split("\\.")[0]);
            i++;
        }
        Arrays.sort(indexSegment);
    }

    /**
     * Interprets http response headers and return http code
     *
     * @param answer String containing first line of http Header
     * @param fromServer InputStream
     * @return HTTP Code
     * @throws IOException
     */
    private static int interpretHeaders(String answer, InputStream fromServer) throws IOException {
        String[] header = HTTPUtilities.parseHttpRequest(answer);
        int httpCode = Integer.parseInt(header[1]);
        
        while (!answer.equals("")) {
            answer = HTTPUtilities.readLine(fromServer);
            System.out.println(answer);
        }
        return httpCode;
    }

    //main
    public static void main(String[] args) {
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
        try {
            server = new URL(args[0]);
            urlPath = server.getPath() + "/";
            
            dataMap = new HashMap<>();
            toPlay = new ConcurrentLinkedDeque<>();
            
            readIndex();
            indexSegment = new int[dataMap.size()];
            fillIndex();
            
            play(playoutDelay);
            download();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
    }
    //
}
