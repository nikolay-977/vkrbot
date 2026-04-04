package ru.skillfactory.vkrbot.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AttachedFile {
    private String fileName;
    private String fileId;
    private Long fileSize;
    private String mimeType;
    private String uploadedAt;
    private Long authorId;
    private String authorName;
}