package org.openmrs.module.labmanagement.api.model;

import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.openmrs.BaseChangeableOpenmrsData;

import javax.persistence.*;
import java.util.Date;

@Entity(name = "labmanagement.Document")
@Table(name = "labmgmt_document")
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONE)
public class Document  extends BaseChangeableOpenmrsData {

    public static final  int WORKSHEET_TEST_RESULT_DOC_GROUP = 1;
    public static final  int TEST_RESULT_DOC_GROUP = 2;


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id", nullable = false)
    private Integer id;

    @Column(name = "file_extension", length = 10)
    private String fileExtension;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "content_size", nullable = false)
    private Long contentSize;

    @Column(name = "content_title", length = 256)
    private String contentTitle;

    @Column(name = "file_name", length = 256)
    private String fileName;

    @Column(name = "document_group")
    private Integer documentGroup;

    @Column(name = "document_group_item_ref", length = 256)
    private String documentGroupItemRef;

    @Column(name = "content_data")
    private byte[] contentData;

    @Column(name = "ref_count", nullable = false)
    private Integer refCount;

    @Column(name = "ref_update", nullable = false)
    private Date refUpdate;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getContentSize() {
        return contentSize;
    }

    public void setContentSize(Long contentSize) {
        this.contentSize = contentSize;
    }

    public String getContentTitle() {
        return contentTitle;
    }

    public void setContentTitle(String contentTitle) {
        this.contentTitle = contentTitle;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Integer getDocumentGroup() {
        return documentGroup;
    }

    public void setDocumentGroup(Integer documentGroup) {
        this.documentGroup = documentGroup;
    }

    public String getDocumentGroupItemRef() {
        return documentGroupItemRef;
    }

    public void setDocumentGroupItemRef(String documentGroupItemRef) {
        this.documentGroupItemRef = documentGroupItemRef;
    }

    public byte[] getContentData() {
        return contentData;
    }

    public void setContentData(byte[] contentData) {
        this.contentData = contentData;
    }

    public Integer getRefCount() {
        return refCount;
    }

    public void setRefCount(Integer refCount) {
        this.refCount = refCount;
    }

    public Date getRefUpdate() {
        return refUpdate;
    }

    public void setRefUpdate(Date refUpdate) {
        this.refUpdate = refUpdate;
    }
}
