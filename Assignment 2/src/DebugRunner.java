public class DebugRunner {
    public static void main(String[] args) {
        MusicOrganizer organizer = new MusicOrganizer();
        organizer.addFile("audio/Beethoven-FurElise.mp3");
        organizer.addFile("audio/Beethoven-MoonlightSonata.mp3");
        organizer.addFile("audio/Mozart-EineKleineNachtmusik.mp3");
        organizer.listAllFiles();
    }
}