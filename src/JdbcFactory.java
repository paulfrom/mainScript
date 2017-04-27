import lombok.AllArgsConstructor;
import lombok.Data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Created by liusonglin on 2017/4/27.
 */
@Data
@AllArgsConstructor
public class JdbcFactory {
    private String url;
    private String userName;
    private String password;
    private String driverClass;

    public Connection getConnection(){
        try {
            return DriverManager.getConnection(this.url,this.userName,this.password);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

}
