package pozoriste1.demo.plays;

import java.time.LocalDate;
import java.time.LocalTime;

public class PerformanceDTO {

    private String id;
    private String playId;
    private LocalDate date;
    private LocalTime time;
    private String auditoriumName;

    public PerformanceDTO(String id, String playId, LocalDate date, LocalTime time, String auditoriumName) {
        this.id = id;
        this.playId = playId;
        this.date = date;
        this.time = time;
        this.auditoriumName = auditoriumName;
    }

    public String getId() { return id; }
    public String getPlayId() { return playId; }
    public LocalDate getDate() { return date; }
    public LocalTime getTime() { return time; }
    public String getAuditoriumName() { return auditoriumName; }
}
