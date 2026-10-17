/**
 * STUB with the same method names as the textbook's MusicPlayer, used only so
 * this project compiles and can be tested without audio hardware.
 * In BlueJ, keep the textbook's real MusicPlayer instead of this file.
 */
public class MusicPlayer
{
    private boolean playing = false;

    public void startPlaying(String filename)
    {
        playing = true;
        System.out.println("[player] started: " + filename);
    }

    public void playAndWait(String filename)
    {
        System.out.println("[player] played sample of: " + filename);
    }

    public void stop()
    {
        if (playing) {
            System.out.println("[player] stopped");
            playing = false;
        }
    }
}