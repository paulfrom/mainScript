package other;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by liusonglin on 2017/3/24.
 */
@Data
public class RemoteResult<T>{

    private String code;


    private List<T> data = new ArrayList<T>();
}
