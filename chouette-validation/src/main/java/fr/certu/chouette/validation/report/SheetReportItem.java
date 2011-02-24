/**
 * 
 */
package fr.certu.chouette.validation.report;

import lombok.Getter;
import fr.certu.chouette.plugin.report.ReportItem;

/**
 * @author zbouziane
 *
 */
public class SheetReportItem extends ReportItem implements Comparable<SheetReportItem>
{
	@Getter private int order;
	/**
	 * 
	 */
	public SheetReportItem(String key, int order)
	{
		setMessageKey(key);
		setStatus(STATE.UNCHECK);

	}
	
	/* (non-Javadoc)
	 * @see fr.certu.chouette.plugin.report.Report#addItem(fr.certu.chouette.plugin.report.ReportItem)
	 */
	@Override
	public void addItem(ReportItem item) 
	{
		super.addItem(item);
		int status = getStatus().ordinal();
		int itemStatus = item.getStatus().ordinal();
		if (itemStatus > status) setStatus(item.getStatus());
	}

	@Override
	public int compareTo(SheetReportItem arg0) {
		return order-arg0.order;
	}
	

}
