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
/**
 * Created by liusonglin on 2017/4/27.
 */
public class DeliverWork {

    public static FileTemplate fileTemplate ;

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
//        JdbcFactory jdbcFactoryChanye=new JdbcFactory("jdbc:mysql://127.0.0.1:3306/fp_guimin?useUnicode=true&characterEncoding=UTF-8","root","111111","com.mysql.jdbc.Driver");
//        Connection connection = jdbcFactoryChanye.getConnection();
//        final String selectAccount= "select id,project_from from fp_project_apply";
//
//        final String insertFile = "";
//
//        try {
//            PreparedStatement ps = connection.prepareStatement(selectAccount);
//            ResultSet resultSet = ps.executeQuery();
//            while (resultSet.next()){
//                String id = resultSet.getString(1);
//                String from = resultSet.getString(2);
//                System.out.println(id+"!!!~~~~~~~~~!!!!"+from);
//                syncTheFile(id,from);
//            }
//            ps.close();
//            connection.close();
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
        syncTheFile("10","3491377537858648");
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


    private static String saveIntoWeed(RemoteFile remoteFile, String projectId){
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
