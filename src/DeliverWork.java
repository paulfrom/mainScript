import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.lokra.seaweedfs.core.FileSource;
import org.lokra.seaweedfs.core.FileTemplate;
import org.lokra.seaweedfs.core.file.FileHandleStatus;
import other.RemoteFile;
import other.RemoteResult;

import javax.mail.MessagingException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by liusonglin on 2017/4/27.
 */
public class DeliverWork {

    public static FileTemplate fileTemplate ;

    public static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    final static String selectAccount= "select id,project_from from fp_project_apply";

    static Connection connection;

    final static String insertFile = "INSERT INTO fp_file (post_time,type,enum_value,data_id,url,name,content_type,size,status,resources ) VALUES(?,?,?,?,?,?,?,?,?,? ) ";


    public static FileTemplate getFileTemplate(){
        if(fileTemplate!=null){
            return fileTemplate;
        }else {
            FileSource fileSource = new FileSource();
            fileSource.setHost("localhost");
            fileSource.setPort(Integer.parseInt("9333"));
            try {
                fileSource.startup();
            } catch (IOException e) {
                e.printStackTrace();
            }
            fileTemplate =  new FileTemplate(fileSource.getConnection());
            return fileTemplate;
        }
    }

    public static void main(String[] args) throws UnsupportedEncodingException, MessagingException {
        JdbcFactory jdbcFactoryChanye=new JdbcFactory("jdbc:mysql://127.0.0.1:3306/fp_guimin?useUnicode=true&characterEncoding=UTF-8","root","111111","com.mysql.jdbc.Driver");
        connection = jdbcFactoryChanye.getConnection();
        Map<String,String> map = new HashMap();

        try {
            PreparedStatement ps = connection.prepareStatement(selectAccount);
            ResultSet resultSet = ps.executeQuery();
            while (resultSet.next()){
                map.put(resultSet.getString(1),resultSet.getString(2));
            }
            ps.close();
            map.forEach((k,v)->{
                syncTheFile(k,v);
            });
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }finally {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean syncTheFile(String projectId,String projectForm){
        String resultBody = getResultBody("http://114.55.100.212:8217/web/leavePoorFileService?id="+projectForm);
        if(resultBody!=null) {
            RemoteResult<RemoteFile> remoteFileRemoteResult = null;
            try {
                remoteFileRemoteResult = JSON.parseObject(resultBody, new TypeReference<RemoteResult<RemoteFile>>() {
                });
                if (remoteFileRemoteResult!=null && remoteFileRemoteResult.getCode().equals("OK")) {
                    for(RemoteFile item:remoteFileRemoteResult.getData()){
                        String result = saveIntoWeed(item, projectId);
                        String sync = "1";
                        if (!"success".equals(result)) {
                            sync = result; //表示同步失败
                        }
                        //2表示是文件
//                        projectApplyMapper.insertProjectBak(projectForm, JSON.toJSONString(item), "2", sync);
                    }
                }
            } catch (Throwable throwable) {
                return false;
            }
        }
        return true;
    }

    public FileSource getfileSource() throws IOException {
        FileSource fileSource = new FileSource();
        fileSource.setHost("localhost");
        fileSource.setPort(Integer.parseInt("9333"));
        fileSource.startup();
        return fileSource;
    }


    private static String saveIntoWeed(RemoteFile remoteFile, String projectId) throws SQLException {
        String url = "http://59.215.226.174/WebDiskServerDemo/doc?doc_id="+remoteFile.getFileId();
        CloseableHttpClient httpClient = null;
        CloseableHttpResponse response = null;
        String res = "";
        try {
            httpClient = HttpClients.createSystem();
            // 创建http请求(get方式)
            HttpGet httpget = new HttpGet(url);
            response = httpClient.execute(httpget);
            HttpEntity result = response.getEntity();
            String fileName = remoteFile.getFileName();
            FileHandleStatus fileHandleStatus = getFileTemplate().saveFileByStream(fileName,new ByteArrayInputStream(EntityUtils.toByteArray(result)));
            System.out.println(fileHandleStatus);
            File file = new File();
            file.setContentType(result.getContentType().getValue());
            file.setDataId(Integer.parseInt(projectId));
            file.setName(fileName);
            if(fileName.contains(".bmp")||fileName.contains(".jpg")||fileName.contains(".jpeg")||fileName.contains(".png")||fileName.contains(".gif")) {
                file.setType(1);
            }else if(fileName.contains(".doc")||fileName.contains(".docx")){
                file.setType(2);
            }else if(fileName.contains(".xlsx")||fileName.contains("xls")) {
                file.setType(3);
            }else if(fileName.contains(".pdf")) {
                file.setType(4);
            }else {
                file.setType(5);
            }
            String accessUrl = "/"+fileHandleStatus.getFileId().replaceAll(",","/").concat("/").concat(fileName);
            file.setUrl(accessUrl);
            file.setSize(fileHandleStatus.getSize());
            file.setPostTime(new java.util.Date());
            file.setStatus(0);
            file.setEnumValue("123123");
            file.setResources(1);
//            JdbcFactory jdbcFactoryChanye=new JdbcFactory("jdbc:mysql://127.0.0.1:3306/fp_guimin?useUnicode=true&characterEncoding=UTF-8","root","111111","com.mysql.jdbc.Driver");
//            Connection connection = jdbcFactoryChanye.getConnection();
            DatabaseMetaData dmd= connection.getMetaData();
            PreparedStatement ps = connection.prepareStatement(insertFile,new String[]{"ID"});
            ps.setString(1,sdf.format(file.getPostTime()) );
            ps.setInt(2, file.getType());
            ps.setString(3, file.getEnumValue());
            ps.setInt(4,file.getDataId());
            ps.setString(5,url);
            ps.setString(6,file.getName());
            ps.setString(7,file.getContentType());
            ps.setLong(8,file.getSize());
            ps.setInt(9,file.getStatus());
            ps.setInt(10,file.getResources());
            ps.executeUpdate();
            if(dmd.supportsGetGeneratedKeys()) {
                ResultSet rs= ps.getGeneratedKeys();
                while(rs.next()) {
                    System.out.println(rs.getLong(1));
                }
            }
            ps.close();
            res = "success";
        } catch (ClientProtocolException e) {
            e.printStackTrace();
            res = e.getMessage();
        } catch (IOException e) {
            e.printStackTrace();
            res = e.getMessage();
        }finally {
            if(httpClient!=null){
                try {
                    httpClient.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (response!=null){
                try {
                    response.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return res;
    }

    private static String getResultBody(String url){
        CloseableHttpClient httpClient = null;
        CloseableHttpResponse response = null;
        String resultBody = null;
        try {
            httpClient = HttpClients.createDefault();
            // 创建http请求(get方式)
            HttpGet httpget = new HttpGet(url);
            response = httpClient.execute(httpget);
            HttpEntity result = response.getEntity();
            resultBody = EntityUtils.toString(result);
        }catch (Exception ex){
            ex.printStackTrace();
        } finally {
            try {
                if(response!=null){
                    response.close();
                }
                if(httpClient!=null){
                    httpClient.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return resultBody;
    }


}
