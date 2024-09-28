package fileupload;

import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import javax.inject.Named;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.primefaces.model.file.UploadedFile;

import com.google.gson.Gson;

import abbyy.ocr.Data;

import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import ch.ivyteam.ivy.application.IApplication;

import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import java.net.http.HttpRequest;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublishers;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;

@Named
@ManagedBean
@ViewScoped
public class Bean {

    private UploadedFile file = null;

    public void upload(Data data) throws IOException {
    	if (file == null) return;
    
        String filename = file.getFileName();
        FacesMessage message = new FacesMessage("Successful", filename + " is uploaded.");
        FacesContext.getCurrentInstance().addMessage(null, message);
        String appPath = IApplication.current().getFileArea().toString();
        File _file = new File(appPath, filename);
        try (FileOutputStream fos = new FileOutputStream(_file)) {
			fos.write(file.getContent());
			fos.flush();
		}
        data.setFilePath(_file.toPath());
    }
    
    public String process(Path filePath) throws Exception {
        String applicationId = "80cf15ff-5bb6-4de3-8b0f-d0090e632165";
        String password = "8IXuEGCCCURtYocMMZqgg3s/";
        String serverUrl = "https://cloud-eu.ocrsdk.com";
        String auth = "Basic " + Base64.getEncoder()
                .encodeToString((applicationId + ":" + password).getBytes(StandardCharsets.UTF_8));
        
        HttpClient client = HttpClient.newHttpClient();
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/v2/processImage?language=German&exportFormat=txtUnstructured"))
                .header("Content-Type", "application/octet-stream")
                .header("Authorization", auth)
                .POST(BodyPublishers.ofFile(filePath))
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        Gson gson = new Gson();
        Task task = gson.fromJson(response.body(), Task.class);
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        while (task.getStatus().equals("Queued")) {
            HttpRequest _request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/getTaskStatus?taskId=" + task.getTaskId()))
                    .header("Authorization", auth)
                    .build();

            HttpResponse<String> _response = client.send(_request, HttpResponse.BodyHandlers.ofString());
            ByteArrayInputStream input = new ByteArrayInputStream(_response.body().getBytes(StandardCharsets.UTF_8));
            Document doc = builder.parse(input);
            doc.getDocumentElement().normalize();
            NodeList taskNodeList = doc.getElementsByTagName("task");
            
            if (taskNodeList.getLength() > 0) {
                Element taskElement = (Element) taskNodeList.item(0);
                
                String status = taskElement.getAttribute("status");
                
                if (status.equals("Completed")) {
                    task.setStatus("Completed");

                    String resultUrl = taskElement.getAttribute("resultUrl");
                    HttpRequest __request = HttpRequest.newBuilder()
                    		.uri(URI.create(resultUrl))
                    		.GET()
                    		.build();
                    HttpResponse<String> __response = client.send(__request, HttpResponse.BodyHandlers.ofString());
                    return __response.body();
                }
            }
        }
        return null;
    }

    public UploadedFile getFile() {
        return file;
    }

    public void setFile(UploadedFile newFile) {
        file = newFile;
    }
}

class Task {
    private String taskId;
    private String status;
    private String[] returnUrls;

    public String getTaskId() {
        return taskId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        status = v;
    }
    
    public String[] getReturnUrls() {
    	return returnUrls;
    }
}
