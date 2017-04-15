package com.example.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.RequestWrapper;
import java.io.File;
import java.io.IOException;

/**
 * Created by hpxue on 04/15/17.
 */
@Controller
public class UploadDownloadController {
    private static final Logger logger = LoggerFactory.getLogger(UploadDownloadController.class);

    @RequestMapping("/")
    public String home() {
        return "home";
    }

    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    public String upload(HttpServletRequest request, @RequestParam String description, @RequestParam MultipartFile file, Model model) throws IOException {
        if (!file.isEmpty()) {
            String path = "C:\\Users\\hpxue\\Desktop\\New folder";
            String filename = file.getOriginalFilename();
            File filePath = new File(path, filename);
            if (!filePath.getParentFile().exists()) {
                filePath.mkdirs();
            }
            file.transferTo(filePath);
            model.addAttribute("filename", "eureka_consumer_1.png");
            return "download";
        } else {
            return "error";
        }
    }

    @RequestMapping(value = "/download")
    public ResponseEntity<byte[]> download(HttpServletRequest request) throws IOException {
        File file = new File("C:\\Users\\hpxue\\Desktop\\New folder","eureka_consumer_1.png");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDispositionFormData("attachment", file.getName());
        headers.setContentType(MediaType.IMAGE_PNG);
        return new ResponseEntity<byte[]>(FileCopyUtils.copyToByteArray(file), headers, HttpStatus.CREATED);
    }
}
