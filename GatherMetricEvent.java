
public class GatherMetricEvent {
	long previousTime, targetTime;
	String metricName;
	int periodicity;
	
	GatherMetricEvent(long previousTime, long targetTime, String metricName, int periodicity) {
		this.previousTime = previousTime;
		this.targetTime = targetTime;
		this.metricName = metricName;
		this.periodicity = periodicity;
	}
}
