package com.wuji.assistant.server.admin.prompt;

/**
 * 保存提示词草稿 / 可选立即发布请求。
 *
 * @author liudy
 */
public class AdminPromptVersionCreateRequest {

    private String name;
    private String role;
    private String content;
    private String changeNote;
    private boolean publish;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getChangeNote() {
        return changeNote;
    }

    public void setChangeNote(String changeNote) {
        this.changeNote = changeNote;
    }

    public boolean isPublish() {
        return publish;
    }

    public void setPublish(boolean publish) {
        this.publish = publish;
    }
}
