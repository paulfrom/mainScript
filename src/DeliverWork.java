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
import java.net.URLEncoder;
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
                throwable.printStackTrace();
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


    private static String saveIntoWeed(RemoteFile remoteFile, String projectId) throws SQLException, UnsupportedEncodingException {
        String url = "http://59.215.226.174/WebDiskServerDemo/doc?doc_id="+URLEncoder.encode(remoteFile.getFileId(), "utf-8");
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
            if(result!=null && result.getContentType()!=null && result.getContentType().getValue()!=null){
                file.setContentType(result.getContentType().getValue());
            }else {
                file.setContentType("application/error");
            }
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
            file.setEnumValue(findFileType(remoteFile.getFileType()));
            file.setResources(1);
//            JdbcFactory jdbcFactoryChanye=new JdbcFactory("jdbc:mysql://127.0.0.1:3306/fp_guimin?useUnicode=true&characterEncoding=UTF-8","root","111111","com.mysql.jdbc.Driver");
//            Connection connection = jdbcFactoryChanye.getConnection();
            DatabaseMetaData dmd= connection.getMetaData();
            PreparedStatement ps = connection.prepareStatement(insertFile,new String[]{"ID"});
            ps.setString(1,sdf.format(file.getPostTime()) );
            ps.setInt(2, file.getType());
            ps.setString(3, file.getEnumValue());
            ps.setInt(4,file.getDataId());
            ps.setString(5,file.getUrl());
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
        }catch (Exception ex){
            ex.printStackTrace();
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

    /**
     * 将发改出入的文件类型与我们系统对象
     * @param fileType  发改委传入的文件类型
     * @return
     */
    public static String findFileType(String fileType) {
        Map map = new HashMap<String, String>();
        //企业营业执照
        map.put("营业执照", FileConstant.BUSINESS_LICENSE_FILE);
        //近三年的财务报表
        //上传投资申请
        map.put("企业投资项目申请报告核准", FileConstant.INVESTMENT_APPLY_FILE);
        //相关资质文件
        map.put("相关资质文件", FileConstant.INTELLIGENCE_FILE);
        //增信文件
        //企业其他所需文件
        map.put("施工许可证核发", FileConstant.OTHER_FILE);
        map.put("农用地转用审批", FileConstant.OTHER_FILE);
        map.put("建设项目用地预审", FileConstant.OTHER_FILE);
        map.put("节能评估报告审查或节能登记表备案", FileConstant.OTHER_FILE);
        map.put("其他行政审批事项", FileConstant.OTHER_FILE);
        map.put("土地征收审批", FileConstant.OTHER_FILE);
        map.put("重大规划、重点工程项目气候可行性论证", FileConstant.OTHER_FILE);
        map.put("宗教活动场所内改建或者新建建筑物审批", FileConstant.OTHER_FILE);
        map.put("港口岸线使用审批", FileConstant.OTHER_FILE);
        map.put("大型基本建设工程开工前的文物考古调查、勘察", FileConstant.OTHER_FILE);
        map.put("企业投资项目备案", FileConstant.OTHER_FILE);
        map.put("防雷装置设计审核", FileConstant.OTHER_FILE);
        map.put("新建、扩建、改建、建设工程避免危害气象探测环境审批", FileConstant.OTHER_FILE);
        map.put("职业病危害预评价报告审核", FileConstant.OTHER_FILE);
        map.put("占用征收林地预审", FileConstant.OTHER_FILE);
        map.put("航道通航条件影响评价审核", FileConstant.OTHER_FILE);
        map.put("移民安置规划审核", FileConstant.OTHER_FILE);
        map.put("核电厂工程消防初步设计审批", FileConstant.OTHER_FILE);
        map.put("煤矿开采方案设计审批", FileConstant.OTHER_FILE);
        map.put("建设项目水资源论证报告书审批与取水许可", FileConstant.OTHER_FILE);
        map.put("建设水工程规划审查", FileConstant.OTHER_FILE);
        map.put("河道管理范围内建设项目及采砂等有关活动审批", FileConstant.OTHER_FILE);
        map.put("建设项目水土保持方案审批", FileConstant.OTHER_FILE);
        map.put("水能资源使用权有偿出让（转让）审批", FileConstant.OTHER_FILE);
        map.put("省管大坝坝顶兼做公路、大坝管理和保护范围内修建码头、鱼塘及险坝改变运行方式审批", FileConstant.OTHER_FILE);
        map.put("占用农灌水源、水利工程设施或者在水利工程设施管理和保护范围内进行有关活动的审批", FileConstant.OTHER_FILE);
        map.put("国家管理外的其他一般水文（测）站的设立、迁移以及建设影响水文监测环境工程审批", FileConstant.OTHER_FILE);
        map.put("核设施建造许可证核发", FileConstant.OTHER_FILE);
        map.put("建设项目环境保护设施验收许可", FileConstant.OTHER_FILE);
        map.put("建设项目环境影响报告书许可", FileConstant.OTHER_FILE);
        map.put("人防工程设计文件审核", FileConstant.OTHER_FILE);
        map.put("人防工程及设备设施拆除审批", FileConstant.OTHER_FILE);
        map.put("建设工程消防设计审核", FileConstant.OTHER_FILE);
        map.put("初步设计审批（含实施方案、重大设计变更、概算调整审批）", FileConstant.OTHER_FILE);
        map.put("新建、改建、扩建建设工程抗震设防要求的确定", FileConstant.OTHER_FILE);
        map.put("在江河、湖泊新建、改建或者扩大入河排污口审核", FileConstant.OTHER_FILE);
        map.put("在国家基本水文测站上下游建设影响水文监测工程的许可", FileConstant.OTHER_FILE);
        map.put("施工图设计文件审查", FileConstant.OTHER_FILE);
        map.put("供地方案审批", FileConstant.OTHER_FILE);
        map.put("建设项目压覆矿产资源审批", FileConstant.OTHER_FILE);
        map.put("地质灾害危险性评估", FileConstant.OTHER_FILE);
        map.put("地质公园范围内工程建设项目审批", FileConstant.OTHER_FILE);
        map.put("选址意见书核发", FileConstant.OTHER_FILE);
        map.put("建设工程规划许可证核发", FileConstant.OTHER_FILE);
        map.put("建设用地规划许可证核发", FileConstant.OTHER_FILE);
        map.put("超限高层建筑工程抗震设防审批", FileConstant.OTHER_FILE);
        map.put("风景名胜区内建设项目选址核准", FileConstant.OTHER_FILE);
        //项目其他文件上传
        map.put("其他材料", FileConstant.TWO_STAGE_FILE);
        map.put("建设项目环境影响报告表许可", FileConstant.TWO_STAGE_FILE);
        map.put("建设项目环境影响登记表备案", FileConstant.TWO_STAGE_FILE);
        map.put("项目建议书审批", FileConstant.TWO_STAGE_FILE);
        map.put("项目基本情况", FileConstant.TWO_STAGE_FILE);
        map.put("建设项目安全预评价", FileConstant.TWO_STAGE_FILE);
        map.put("建设工程消防设计备案", FileConstant.TWO_STAGE_FILE);
        map.put("可行性研究报告审批", FileConstant.TWO_STAGE_FILE);
        //股东会或董事会同意产业基金以股权投资等方试投入决议
        map.put("项目申报主体股东会或董事会同意产业基金以股权投资等方式投入的决议", FileConstant.DIRECTORS_FILE);
        //基金使用方案
        map.put("基金使用方案", FileConstant.FUND_PLAN_FILE);
        //基金其它所需文件
        //项目带动建档立卡贫困人口增收承诺书
        map.put("项目申报主体带动建档立卡贫困人口增收的承诺书", FileConstant.COMMITMENT_FILE);
        //带动增收其它所需文件

        if (map.containsKey(fileType)) {
            return (String)map.get(fileType);
        }
        return FileConstant.TWO_STAGE_FILE; //项目其他文件上传
    }


}
