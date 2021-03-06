package org.pqh.util;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.log4j.Logger;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by 10295 on 2016/8/4.
 * 爬虫工具类
 */
public class CrawlerUtil {
    private static Logger log= Logger.getLogger(CrawlerUtil.class);
    //请求cookie信息
    public static String cookie= "";
    //用户浏览器标识
    private static String userAgent=PropertiesUtil.getProperties("User-Agent",String.class);
    //连接超时时间
    private static int timeout=PropertiesUtil.getProperties("timeout",Integer.class);;
    //发送的表单数据
    public static Map<String,String> formMap=new HashMap<String, String>();

    /**
     * httpclient get请求封装
     * @param href
     * @return
     */
    public static CloseableHttpResponse doGet(String href){
        log.debug("向地址："+href+"发送get请求");
        CloseableHttpClient closeableHttpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(href);
        httpGet.setHeader("User-Agent", userAgent);
        try {
            return closeableHttpClient.execute(httpGet);
        } catch (IOException e) {
            log.info("get请求发生异常,"+timeout+"秒后重新尝试发送请求");
            ThreadUtil.sleep(LogUtil.getLineInfo(), timeout);
            return doGet(href);
        }
    }

    public static HttpEntity makeMultipartEntity(List<NameValuePair> params, final Map<String, File> files) {

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();

        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE); //如果有SocketTimeoutException等情况，可修改这个枚举

        //builder.setCharset(Charset.forName("UTF-8"));
        //不要用这个，会导致服务端接收不到参数

        if (params != null && params.size() > 0) {

            for (NameValuePair p : params) {

                builder.addTextBody(p.getName(), p.getValue(), ContentType.TEXT_PLAIN.withCharset("UTF-8"));

            }

        }

        if (files != null && files.size() > 0) {

            Set<Map.Entry<String, File>> entries = files.entrySet();

            for (Map.Entry<String, File> entry : entries) {

                builder.addPart(entry.getKey(), new FileBody(entry.getValue()));

            }

        }

        return builder.build();

    }

    /**
     *
     * @param url 爬虫的网址
     * @param tClass 返回的对象类型
     * @param method 请求方式
     * @param <T> 返回的对象类型
     * @param params 请求参数
     * @return  返回文档信息
     */
    public static <T>T jsoupGet(String url, Class<T> tClass, Connection.Method method,String ...params){
        Connection connection=null;
        ObjectMapper objectMapper=new ObjectMapper();
        objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS,true);

        log.debug("连接URL:"+url);
        int i=0;
        String json=null;
        try {
            if(tClass== org.dom4j.Document.class){
                SAXReader saxReader=new SAXReader();
                return (T) saxReader.read(url);
            }
//            String param[]=url.contains("?")?url.substring(url.indexOf("?")+1).split("="):null;
            connection = Jsoup.connect(url).header("Cookie",cookie).userAgent(userAgent).timeout(timeout*1000).data(params).ignoreContentType(true);


            if(tClass==Document.class){
                if (method.equals(Connection.Method.GET)) {
                    return (T) connection.get();
                } else if (method.equals(Connection.Method.POST)) {
                    return (T) connection.post();
                } else {
                    throw new RuntimeException("不支持" + method + "请求");
                }
            }
            else if(tClass==String.class){
                return (T) connection.execute().body();
            }else if(tClass==JsonNode.class){
                json=method.equals(Connection.Method.GET)?connection.get().body().text():connection.post().body().text();
                return (T) objectMapper.readTree(json);
            }else {
                throw new RuntimeException("返回值不支持"+tClass.getName()+"这种类型");
            }
        }
        catch(JsonParseException e){

            StringUtil stringUtil=new StringUtil(json);
            try {
                if(url.contains("vstorage")){
                    return (T)objectMapper.readTree(stringUtil.jsonValueEscape("title","author","subtitle"));
                }else{
                    return (T)objectMapper.readTree(stringUtil.convert());
                }
            } catch (JsonParseException e1) {
                if(!url.contains("vstorage")){
                    return (T)stringUtil.fuckJson();
                }else{
                    return null;
                }
            }catch (IOException e1) {
                log.error("异常信息"+e1.getMessage());
                return null;
            }
        }
        catch (HttpStatusException e){
            if(e.getStatusCode()==404){
                return null;
            }else{
                return jsoupGet(url,tClass,method);
            }
        }
        catch (IOException e) {
            log.error("网络异常"+e.getMessage());
            while(isReachable("www.baidu.com")==null){
                log.error("无法连接到百度，应该是断网了，30秒后重新尝试连接");
                ThreadUtil.sleep(30);
            }
            ThreadUtil.sleep(5);
            return jsoupGet(url,tClass,method);
        } catch (DocumentException e) {
            log.error("解析xml文档出错，异常信息"+e.getMessage());
            if(e.getMessage().contains("在文档的元素内容中找到无效的 XML 字符")||e.getMessage().contains("前言中不允许有内容")||e.getMessage().contains("HTTP response code: 502")) {
                return null;
            }
            while(isReachable("www.baidu.com")==null){
                log.error("无法连接到百度，应该是断网了，30秒后重新尝试连接");
                ThreadUtil.sleep(30);
            }
            return jsoupGet(url, tClass, method);
        }

    }

    /**
     * 传入需要连接的IP，返回是否连接成功
     * @param remoteInetAddr
     * @return
     */
    public static String isReachable(String remoteInetAddr) {
        String ip=null;
        try {
            ip=InetAddress.getByName(remoteInetAddr).getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
        return ip;
    }

}
