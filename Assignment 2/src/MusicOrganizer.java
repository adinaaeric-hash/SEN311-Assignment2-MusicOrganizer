import java.util.ArrayList;
import java.util.Iterator;

public class MusicOrganizer
{
    private ArrayList<Track> tracks;
    private MusicPlayer player;

    public MusicOrganizer()
    {
        tracks = new ArrayList<Track>();
        player = new MusicPlayer();
    }

    // ---------- Collection basics ----------

    public void addFile(String filename)
    {
        tracks.add(new Track(filename));
    }

    public void addTrack(Track track)
    {
        tracks.add(track);
    }

    public int numberOfFiles()
    {
        return tracks.size();
    }

    /** Same as numberOfFiles(); this is the name used in the textbook's Code 4.1. */
    public int getNumberOfFiles()
    {
        return tracks.size();
    }

    /** Read-only view of the tracks. Used by the GUI only; not part of any exercise. */
    public java.util.List<Track> getTracks()
    {
        return java.util.Collections.unmodifiableList(tracks);
    }

    // ---------- Exercises 4.14 - 4.16 ----------

    /** 4.14: prints an error message if index is not valid. */
    public void checkIndex(int index)
    {
        if (tracks.isEmpty()) {
            System.out.println("Invalid index " + index + ": the collection is empty.");
        } else if (index < 0 || index >= tracks.size()) {
            System.out.println("Invalid index " + index
                + ". Valid range is 0 to " + (tracks.size() - 1));
        }
    }

    /** 4.15: true if index is valid for the current collection. */
    public boolean validIndex(int index)
    {
        return index >= 0 && index < tracks.size();
    }

    /** 4.16: uses validIndex before calling get. */
    public void listFile(int index)
    {
        if (validIndex(index)) {
            System.out.println(tracks.get(index).getDetails());
        }
    }

    /** 4.16: uses validIndex before calling remove. */
    public void removeFile(int index)
    {
        if (validIndex(index)) {
            tracks.remove(index);
        }
    }

    // ---------- Exercises 4.20 - 4.28 ----------

    /** 4.20: for-each over the whole collection. */
    public void listAllFiles()
    {
        for (Track track : tracks) {
            System.out.println(track.getDetails());
        }
    }

    /** 4.24: same, but with a position counter. */
    public void listAllFilesWithPosition()
    {
        int position = 0;
        for (Track track : tracks) {
            System.out.println(position + ": " + track.getFilename());
            position++;
        }
    }

    /** 4.25: lists only file names containing searchString. */
    public void listMatching(String searchString)
    {
        for (Track track : tracks) {
            if (track.getFilename().contains(searchString)) {
                System.out.println(track.getDetails());
            }
        }
    }

    /** 4.26: as listMatching, but always reports how many files matched. */
    public void listMatchingWithMessage(String searchString)
    {
        int count = 0;
        for (Track track : tracks) {
            if (track.getFilename().contains(searchString)) {
                System.out.println(track.getDetails());
                count++;
            }
        }
        if (count == 0) {
            System.out.println("No files matched: " + searchString);
        } else {
            System.out.println(count + " file(s) matched: " + searchString);
        }
    }

    /** 4.27: plays a sample of every track by the given artist, one after another. */
    public void playByArtist(String artist)
    {
        int count = 0;
        for (Track track : tracks) {
            if (track.getArtist().contains(artist)) {
                player.playAndWait(track.getFilename());
                track.incrementPlayCount();
                count++;
            }
        }
        if (count == 0) {
            System.out.println("No tracks found by artist: " + artist);
        }
    }

    // ---------- Exercises 4.35 - 4.39 ----------

    /** 4.36 + 4.38: stops current playback, plays the track, counts the play. */
    public void playTrack(int index)
    {
        if (validIndex(index)) {
            player.stop();                       // 4.38
            Track track = tracks.get(index);
            player.startPlaying(track.getFilename());
            track.incrementPlayCount();          // 4.36
        }
    }

    public void stopPlaying()
    {
        player.stop();
    }

    /**
     * 4.37: set the year of a track through the organizer.
     * The year is checked first: it must be between Track.MIN_YEAR and the current year.
     * @return true if the year was stored, false if the index or the year was not valid
     */
    public boolean setYear(int index, int year)
    {
        if (!validIndex(index)) {
            return false;
        }
        if (!Track.isValidYear(year)) {
            System.out.println("Invalid year " + year + ". Please enter a year between "
                + Track.MIN_YEAR + " and " + Track.maxYear() + ".");
            return false;
        }
        tracks.get(index).setYear(year);
        return true;
    }
    

    /** 4.39: removes every track whose title contains searchString, and says how many. */
    public void removeByTitle(String searchString)
    {
        int removed = 0;
        Iterator<Track> it = tracks.iterator();
        while (it.hasNext()) {
            Track track = it.next();
            if (track.getTitle().contains(searchString)) {
                it.remove();          // safe: removes via the iterator
                removed++;
            }
        }
        if (removed == 0) {
            System.out.println("No track title contains: " + searchString);
        } else {
            System.out.println("Removed " + removed + " track(s) with title containing: " + searchString);
        }
    }
}