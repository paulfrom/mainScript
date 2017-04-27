import lombok.Data;

import java.util.Date;

/**
 * Created by xulong on 16/12/28.
 */
@Data
public class File{

	private Integer type;

	private String enumValue;

	private Integer dataId;

	private String url;

	private String name;

	private String contentType;

	private Long size;

	/**
	 * 0:正常,1:删除
	 */
	private Integer status;
	/**
	 * resources : 来源，0-我们系统的、1-发改委系统的
	 */
	private Integer resources;

	private Date postTime;
}
