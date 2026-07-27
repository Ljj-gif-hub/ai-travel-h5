package org.example.traveljava.dto;

public class SceneImageDTO {
    
    private String imgUrl;
    private String source;

    public SceneImageDTO() {
    }

    public SceneImageDTO(String imgUrl, String source) {
        this.imgUrl = imgUrl;
        this.source = source;
    }

    public String getImgUrl() {
        return imgUrl;
    }

    public void setImgUrl(String imgUrl) {
        this.imgUrl = imgUrl;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}