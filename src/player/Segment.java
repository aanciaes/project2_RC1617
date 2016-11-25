/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package player;

/**
 *
 * @author miguel
 */
public class Segment {
 
    private final String quality;
    private final int number;
    private final int offset;
    private final int duration;
    
    public Segment (String quality, int number, int offset, int duration) {
        this.quality=quality;
        this.number=number;
        this.offset=offset;
        this.duration=duration;
    }

    public String getQuality() {
        return quality;
    }

    public int getNumber() {
        return number;
    }

    public int getOffset() {
        return offset;
    }

    public int getDuration() {
        return duration;
    }
}
