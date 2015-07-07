public abstract class RecordQueue {
	String id;
	int maxQueueLength;
	
	RecordQueue(){};
	
	RecordQueue(String id, int maxQueueLength) {
		this.id = id + ":RecQ";
		this.maxQueueLength = maxQueueLength;
	}
	
	public abstract int queueSize() ;

	public abstract boolean put(Record record);
	
	public abstract Record get() ;
	
	public abstract Record get(String name);
	
	public abstract String printRecords();
	
	public abstract String printNewestRecords(int numRecords);
	
	public abstract Record[] dumpNewestRecords(int numRecords);
	
	public abstract Record[] dumpOldestRecords(int numRecords);
	
	public abstract Record[] dumpAllRecords();
	
	public abstract Record[] dumpRecordsFromTimeRange(long startTime, long endTime);

	
	
}
