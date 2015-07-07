
public class Record {
	/**
	 * DERIVED VALUES
	 * These variables can be used to describe a period of time as opposed to just an instant in time as inidicated solely by the
	 * "timestamp" variable.  It is generally expected that "previousTimestamp" be <= "timestamp" and thus "durationms" is 
	 * expected to be >= 0 and expected to be calculated as "timestamp" - "previousTimestamp"
	 */
	long previousTimestamp, durationms;
	//NONE
	
	/**
	 * RAW VALUES
	 * Timestamp should be populated by classes that extend record
	 * The value is generally expected to represent the time at which the event detailed in the Record occurred.
	 */
	long timestamp;
	
	Record(){}
	Record(Record r){
		this.timestamp = r.timestamp;
		this.previousTimestamp = r.previousTimestamp;
		this.durationms = r.durationms;
	}
	public static String[] type1DiffSupport(){
		return new String[]{	"previousTimestamp",
								"durationms"			};
	}
	public static Record type1Diff(Record oldRecord, Record newRecord) {
		Record diffRecord = new Record();
		return type1Diff(oldRecord, newRecord, diffRecord);
	}
	public static Record type1Diff(Record oldRecord, Record newRecord, Record diffRecord) {
		diffRecord.timestamp = newRecord.timestamp;
		diffRecord.previousTimestamp = oldRecord.timestamp;
		diffRecord.durationms = diffRecord.timestamp - diffRecord.previousTimestamp;
		return diffRecord;
	}
	public static String[] type2DiffSupport(){
		return new String[]{	"previousTimestamp",
								"durationms"			};
	}
	public static Record type2Diff(Record[] records) {
		Record diffRecord = new Record();
		return type2Diff(records, diffRecord);
	}
	public static Record type2Diff(Record[] records, Record diffRecord) {
		diffRecord.timestamp = records[records.length - 1].timestamp;
		diffRecord.previousTimestamp = records[0].timestamp;
		diffRecord.durationms = diffRecord.timestamp - diffRecord.previousTimestamp;
		return diffRecord;
	}
	public boolean isBelowThreshold(String metricStr, double val){
		return false;
	}
	public String toString(){
		return "RecTime: " + previousTimestamp + " - " + timestamp + " (" + durationms + "ms)";
	}
}
