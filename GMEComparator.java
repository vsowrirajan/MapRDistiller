import java.util.Comparator;

public class GMEComparator implements Comparator<GatherMetricEvent>{
	public int compare(GatherMetricEvent e1, GatherMetricEvent e2) {
		if(e1.targetTime < e2.targetTime){
			return -1;
		} else if (e2.targetTime < e1.targetTime){
			return 1;
		}
		return e1.metricName.compareTo(e2.metricName);
	}

}
