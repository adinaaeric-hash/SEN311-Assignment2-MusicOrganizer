/**
 * A single music track. Extended for exercises 4.35 and 4.37:
 * playCount (4.35) and year (4.37, my own extra field).
 */
public class Track
{
    private String artist;
    private String title;
    private String filename;
    private int year;        // 4.37: extra field (0 = unknown)
    private int playCount;   // 4.35

    public Track(String artist, String title, String filename)
    {
        this.artist = artist;
        this.title = title;
        this.filename = filename;
        this.year = 0;
        this.playCount = 0;
    }

    /** Build a track from a file name such as "audio/Artist-Title.mp3". */
    public Track(String filename)
    {
        this.filename = filename;
        this.year = 0;
        this.playCount = 0;
        String name = filename.substring(filename.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        int dash = name.indexOf('-');
        if (dash > 0) {
            artist = name.substring(0, dash);
            title = name.substring(dash + 1);
        } else {
            artist = "unknown";
            title = name;
        }
    }

    public String getArtist()   { return artist; }
    public String getTitle()    { return title; }
    public String getFilename() { return filename; }

    // 4.35: play count
    public int getPlayCount()        { return playCount; }
    public void incrementPlayCount() { playCount++; }
    public void resetPlayCount()     { playCount = 0; }

    // 4.37: year accessor and mutator
    public int getYear()          { return year; }
    public void setYear(int year) { this.year = year; }

    public String getDetails()
    {
        String details = artist + ": " + title + " (" + filename + ")";
        if (year > 0) {
            details += ", year " + year;
        }
        return details + ", played " + playCount + " time(s)";
    }
}