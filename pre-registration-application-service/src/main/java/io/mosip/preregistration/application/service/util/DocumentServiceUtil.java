/* 
 * Copyright
 * 
 */
package io.mosip.preregistration.application.service.util;

import static io.mosip.preregistration.application.constant.PreRegApplicationConstant.LOGGER_ID;
import static io.mosip.preregistration.application.constant.PreRegApplicationConstant.LOGGER_IDTYPE;
import static io.mosip.preregistration.application.constant.PreRegApplicationConstant.LOGGER_SESSIONID;

import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import io.mosip.commons.khazana.spi.ObjectStoreAdapter;
import io.mosip.kernel.core.exception.IOException;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.kernel.core.util.JsonUtils;
import io.mosip.kernel.core.util.exception.JsonMappingException;
import io.mosip.kernel.core.util.exception.JsonParseException;
import io.mosip.kernel.core.virusscanner.exception.VirusScannerException;
import io.mosip.kernel.core.virusscanner.spi.VirusScanner;
import io.mosip.preregistration.application.dto.DocumentRequestDTO;
import io.mosip.preregistration.application.errorcodes.DocumentErrorCodes;
import io.mosip.preregistration.application.errorcodes.DocumentErrorMessages;
import io.mosip.preregistration.application.exception.DemographicGetDetailsException;
import io.mosip.preregistration.application.exception.DocumentNotValidException;
import io.mosip.preregistration.application.exception.DocumentSizeExceedException;
import io.mosip.preregistration.application.exception.InvalidDocumentIdExcepion;
import io.mosip.preregistration.core.code.StatusCodes;
import io.mosip.preregistration.core.common.dto.DemographicResponseDTO;
import io.mosip.preregistration.core.common.dto.MainRequestDTO;
import io.mosip.preregistration.core.common.dto.MainResponseDTO;
import io.mosip.preregistration.core.common.entity.DemographicEntity;
import io.mosip.preregistration.core.common.entity.DocumentEntity;
import io.mosip.preregistration.core.config.LoggerConfiguration;
import io.mosip.preregistration.core.exception.InvalidRequestException;
import io.mosip.preregistration.core.util.HashUtill;
import io.mosip.preregistration.core.util.UUIDGeneratorUtil;
import io.mosip.preregistration.core.util.ValidationUtil;

/**
 * This class provides the utility methods for DocumentService
 * 
 * @author Rajath KR
 * @author Tapaswini
 * @since 1.0.0
 */
@Component
public class DocumentServiceUtil {

	/**
	 * Autowired reference for {@link #VirusScanner}
	 */
	private VirusScanner<Boolean, InputStream> virusScan;

	/**
	 * Reference for ${max.file.size} from property file
	 */
	@Value("${max.file.size}")
	private int maxFileSize;

	/**
	 * Reference for ${file.extension} from property file
	 */
	@Value("${preregistration.document.extention}")
	private String fileExtension;

	@Value("${mosip.utc-datetime-pattern}")
	private String utcDateTimePattern;

	@Autowired
	ValidationUtil validationUtil;

	@Autowired
	private CommonServiceUtil commonServiceUtil;

	/**
	 * Reference for ${demographic.resource.url} from property file
	 */
	@Value("${demographic.resource.url}")
	private String demographicResourceUrl;

// 	@Autowired
// 	private FileSystemAdapter fs;

	@Value("${mosip.kernel.objectstore.account-name}")
	private String objectStoreAccountName;

	@Qualifier("S3Adapter")
	@Autowired
	private ObjectStoreAdapter objectStore;

	/**
	 * Logger configuration for DocumentServiceUtil
	 */
	private static Logger log = LoggerConfiguration.logConfig(DocumentServiceUtil.class);

	/**
	 * This method is used to assign the input JSON values to DTO
	 * 
	 * @param documentJsonString pass the document json
	 * @return UploadRequestDTO
	 * @throws JSONException        on json error
	 * @throws JsonParseException   on json parsing error
	 * @throws JsonMappingException on json mapping error
	 * @throws IOException          on input error
	 * @throws ParseException       on parsing error
	 */
	public MainRequestDTO<DocumentRequestDTO> createUploadDto(String documentJsonString, String preRegistrationId)
			throws JSONException, JsonParseException, JsonMappingException, IOException, ParseException {
		log.info(LOGGER_SESSIONID, LOGGER_IDTYPE, LOGGER_ID, "In createUploadDto method of document service util");
		MainRequestDTO<DocumentRequestDTO> uploadReqDto = new MainRequestDTO<>();
		JSONObject documentData = new JSONObject(documentJsonString);
		JSONObject docDTOData = (JSONObject) documentData.get("request");
		DocumentRequestDTO documentDto = (DocumentRequestDTO) JsonUtils.jsonStringToJavaObject(DocumentRequestDTO.class,
				docDTOData.toString());
		uploadReqDto.setId(documentData.get("id").toString());
		uploadReqDto.setVersion(documentData.get("version").toString());
		if (!(documentData.get("requesttime") == null || documentData.get("requesttime").toString().isEmpty())) {
			uploadReqDto.setRequesttime(
					new SimpleDateFormat(utcDateTimePattern).parse(documentData.get("requesttime").toString()));
		} else {
			uploadReqDto.setRequesttime(null);
		}
		uploadReqDto.setRequest(documentDto);
		return uploadReqDto;
	}

	/**
	 * This method assigns the values from DTO to entity
	 * 
	 * @param dto pass the document dto
	 * @return DocumentEntity
	 */
	public DocumentEntity dtoToEntity(MultipartFile file, DocumentRequestDTO dto, String userId,
			String preRegistrationId, DocumentEntity getentity) {
		log.info(LOGGER_SESSIONID, LOGGER_IDTYPE, LOGGER_ID, "In dtoToEntity method of document service util");
		DocumentEntity documentEntity = new DocumentEntity();
		documentEntity.setDocumentId(UUIDGeneratorUtil.generateId());
		documentEntity.setDocId(preRegistrationId + "/" + dto.getDocCatCode() + "_" + documentEntity.getDocumentId());
		DemographicEntity demographicEntity = new DemographicEntity();
		demographicEntity.setPreRegistrationId(preRegistrationId);
		documentEntity.setDemographicEntity(demographicEntity);
		documentEntity.setDocCatCode(dto.getDocCatCode());
		documentEntity.setDocTypeCode(dto.getDocTypCode());
		documentEntity.setDocFileFormat(FilenameUtils.getExtension(file.getOriginalFilename()));
		documentEntity.setStatusCode(StatusCodes.DOCUMENT_UPLOADED.getCode());
		documentEntity.setLangCode(dto.getLangCode());
		documentEntity.setCrDtime(LocalDateTime.now(ZoneId.of("UTC")));
		documentEntity.setCrBy(userId);
		documentEntity.setUpdBy(userId);
		documentEntity.setUpdDtime(LocalDateTime.now(ZoneId.of("UTC")));
		documentEntity.setRefNumber(dto.getRefNumber());
		// documentEntity.setEncryptedDateTime(LocalDateTime.now(ZoneId.of("UTC")));
		return documentEntity;
	}

	/**
	 * This method is used to check whether the key is null
	 * 
	 * @param key pass the key
	 * @return true if key is null, else false
	 */
	public boolean isNull(Object key) {
		log.info(LOGGER_SESSIONID, LOGGER_IDTYPE, LOGGER_ID, "In isNull method of document service util");
		if (key instanceof String) {
			if (key.equals(""))
				return true;
		} else if (key instanceof List<?>) {
			if (((List<?>) key).isEmpty())
				return true;
		} else {
			if (key == null)
				return true;
		}
		return false;

	}

	/**
	 * @return maximum file size defined.
	 */
	public long getMaxFileSize() {
		log.info(LOGGER_SESSIONID, LOGGER_IDTYPE, LOGGER_ID, "In getMaxFileSize method of document service util");
		return Math.multiplyExact(this.maxFileSize, Math.multiplyExact(1024, 1024));
	}

	/**
	 * @return defined document extension.
	 *//*
		 * public String getFileExtension() { log.info(LOGGER_SESSIONID, LOGGER_IDTYPE,
		 * LOGGER_ID "In getFileExtension method of document service util"); return
		 * this.fileExtension; }
		 */

	public String getCurrentResponseTime() {
		log.info(LOGGER_SESSIONID, LOGGER_IDTYPE, LOGGER_ID,
				"In getCurrentResponseTime method of document service util");
		return DateUtils.formatDate(new Date(System.currentTimeMillis()), utcDateTimePattern);
	}

	public String getDateString(Date date) {
		log.info(LOGGER_SESSIONID, LOGGER_IDTYPE, LOGGER_ID, "In getDateString method of document service util");
		return DateUtils.formatDate(date, utcDateTimePattern);
	}

	public Integer parseDocumentId(String documentId) {
		log.info(LOGGER_SESSIONID, LOGGER_IDTYPE, LOGGER_ID, "In parseDocumentId method of document service util");
		try {
			return Integer.parseInt(documentId);
		} catch (NumberFormatException ex) {
			log.error(LOGGER_SESSIONID, LOGGER_IDTYPE, LOGGER_ID,
					"In parseDocumentId method of document service util- " + ex.getMessage());

			throw new InvalidDocumentIdExcepion(DocumentErrorCodes.PRG_PAM_DOC_019.toString(),
					DocumentErrorMessages.INVALID_DOCUMENT_ID.getMessage());
		}
	}

	public boolean isValidCatCode(String catCode) {
		log.info(LOGGER_SESSIONID, LOGGER_IDTYPE, LOGGER_ID, "In isValidCatCode method of document service util");
		if (catCode.equals("POA")) {
			return true;
		} else {
			throw new InvalidRequestException(
					io.mosip.preregistration.core.errorcodes.ErrorCodes.PRG_CORE_REQ_017.toString(),
					io.mosip.preregistration.core.errorcodes.ErrorMessages.INVALID_DOC_CAT_CODE.getMessage(), null);
		}
	}

	public DocumentEntity documentEntitySetter(String destinationPreId, DocumentEntity sourceEntity,
			DocumentEntity destEntity) throws java.io.IOException {
		log.info(LOGGER_SESSIONID, LOGGER_IDTYPE, LOGGER_ID, "In documentEntitySetter method of document service util");
		DocumentEntity copyDocumentEntity = new DocumentEntity();
		if (destEntity != null) {
			copyDocumentEntity.setDocumentId(destEntity.getDocumentId());
		} else {
			copyDocumentEntity.setDocumentId(UUIDGeneratorUtil.generateId());
		}
		DemographicEntity demographicEntity = new DemographicEntity();
		demographicEntity.setPreRegistrationId(destinationPreId);
		copyDocumentEntity.setDemographicEntity(demographicEntity);
		copyDocumentEntity.setDocId(sourceEntity.getDocId());
		String key = sourceEntity.getDocCatCode() + "_" + sourceEntity.getDocumentId();
		InputStream file = objectStore.getObject(objectStoreAccountName,
				sourceEntity.getDemographicEntity().getPreRegistrationId(), null, null, key);
		copyDocumentEntity.setDocHash(HashUtill.hashUtill(IOUtils.toByteArray(file)));
		copyDocumentEntity.setDocName(sourceEntity.getDocName());
		copyDocumentEntity.setDocTypeCode(sourceEntity.getDocTypeCode());
		copyDocumentEntity.setDocCatCode(sourceEntity.getDocCatCode());
		copyDocumentEntity.setDocFileFormat(sourceEntity.getDocFileFormat());
		copyDocumentEntity.setRefNumber(sourceEntity.getRefNumber());
		copyDocumentEntity.setCrBy(sourceEntity.getCrBy());
		copyDocumentEntity.setUpdBy(sourceEntity.getUpdBy());
		copyDocumentEntity.setLangCode(sourceEntity.getLangCode());
		copyDocumentEntity.setEncryptedDateTime(sourceEntity.getEncryptedDateTime());
		copyDocumentEntity.setCrDtime(LocalDateTime.now(ZoneId.of("UTC")));
		copyDocumentEntity.setUpdDtime(LocalDateTime.now(ZoneId.of("UTC")));
		copyDocumentEntity.setStatusCode(StatusCodes.DOCUMENT_UPLOADED.getCode());
		return copyDocumentEntity;
	}

	/**
	 * This method checks the size of uploaded file
	 * 
	 * @param uploadedFileSize pass uploaded file
	 * @return true if file size is within the limit, else false
	 */
	public boolean fileSizeCheck(long uploadedFileSize) {
		log.info(LOGGER_SESSIONID, LOGGER_IDTYPE, LOGGER_ID, "In fileSizeCheck method of document service util");
		long maxAllowedSize = getMaxFileSize();
		if (uploadedFileSize < maxAllowedSize) {
			return true;
		} else {
			throw new DocumentSizeExceedException(DocumentErrorCodes.PRG_PAM_DOC_007.toString(),
					DocumentErrorMessages.DOCUMENT_EXCEEDING_PREMITTED_SIZE.getMessage());
		}
	}

	/**
	 * This method checks the file extension
	 * 
	 * @param file pass uploaded file
	 * @throws DocumentNotValidException if uploaded document is not valid
	 */
	public boolean fileExtensionCheck(MultipartFile file) {
		log.info(LOGGER_SESSIONID, LOGGER_IDTYPE, LOGGER_ID, "In fileExtensionCheck method of document service util");
		// List<String> fileExtensionList =
		// Arrays.asList(fileExtension.split("\\s*,\\s*"));
		List<String> fileExtensionList = Arrays.asList(fileExtension.split(","));
		List<String> fileExtensionTrimmedList = new ArrayList<String>();
		fileExtensionList.forEach(str -> fileExtensionTrimmedList.add(str.trim()));
		if (fileExtensionTrimmedList.contains(FilenameUtils.getExtension(file.getOriginalFilename()).toUpperCase())) {
			return true;
		} else {
			throw new DocumentNotValidException(DocumentErrorCodes.PRG_PAM_DOC_004.toString(),
					DocumentErrorMessages.DOCUMENT_INVALID_FORMAT.getMessage());
		}

	}

	/**
	 * 
	 * @param documentDto DocumentRequestDTO
	 * @return boolean
	 */
	public boolean isValidRequest(DocumentRequestDTO documentDto, String preRegistrationId) {
		log.info(LOGGER_SESSIONID, LOGGER_IDTYPE, LOGGER_ID, "In isValidRequest method of document service util");
		if (isNull(preRegistrationId)) {
			throw new InvalidRequestException(DocumentErrorCodes.PRG_PAM_DOC_018.toString(),
					DocumentErrorMessages.INVALID_PRE_ID.getMessage(), null);
		}
		return true;
	}

	/**
	 * This method checks the file extension
	 * 
	 * @param file pass uploaded file
	 * @throws java.io.IOException
	 * @throws DocumentNotValidException if uploaded document is not valid
	 */
	public void virusScanCheck(MultipartFile file) throws java.io.IOException {
		try {
			log.info(LOGGER_SESSIONID, LOGGER_IDTYPE, LOGGER_ID,
					"In isVirusScanSuccess method of document service util");
			Boolean scanSuccess = virusScan.scanDocument(file.getBytes());
			if (!scanSuccess) {
				throw new VirusScannerException(DocumentErrorCodes.PRG_PAM_DOC_010.toString(),
						DocumentErrorMessages.DOCUMENT_FAILED_IN_VIRUS_SCAN.getMessage());
			}
		} catch (VirusScannerException e) {
			log.error(LOGGER_SESSIONID, LOGGER_IDTYPE, LOGGER_ID, ExceptionUtils.getStackTrace(e));
			log.error(LOGGER_SESSIONID, LOGGER_IDTYPE, LOGGER_ID, e.getMessage());
			throw new VirusScannerException(DocumentErrorCodes.PRG_PAM_DOC_010.toString(),
					DocumentErrorMessages.DOCUMENT_FAILED_IN_VIRUS_SCAN.getMessage());
		}
	}

	public DemographicResponseDTO getPreRegInfoRestService(String preId) {
		log.info(LOGGER_SESSIONID, LOGGER_IDTYPE, LOGGER_ID,
				"In callGetPreRegInfoRestService method of document service util");

		MainResponseDTO<DemographicResponseDTO> getDemographicData = commonServiceUtil.getDemographicData(preId);
		if (getDemographicData.getErrors() != null) {
			throw new DemographicGetDetailsException(getDemographicData.getErrors().get(0).getErrorCode(),
					getDemographicData.getErrors().get(0).getMessage());
		}
		return getDemographicData.getResponse();
	}

	public boolean isMandatoryDocumentDeleted(DemographicEntity demographicEntity)
			throws org.json.simple.parser.ParseException {
		List<String> availableDocuments = demographicEntity.getDocumentEntity().stream().map(doc -> doc.getDocCatCode())
				.collect(Collectors.toList());
		log.info(LOGGER_SESSIONID, LOGGER_IDTYPE, LOGGER_ID, "uploaded documents for user {"
				+ demographicEntity.getPreRegistrationId() + "} ----> {" + availableDocuments + "}");
		List<String> mandatoryDoc = validMandatoryDocuments(demographicEntity);
		log.info(LOGGER_SESSIONID, LOGGER_IDTYPE, LOGGER_ID, "mandatory documents for user {"
				+ demographicEntity.getPreRegistrationId() + "} ----> {" + mandatoryDoc + "}");
		return compareUploadedDocListAndValidMandatoryDocList(availableDocuments, mandatoryDoc);

	}

	public List<String> validMandatoryDocuments(DemographicEntity demographicEntity)
			throws org.json.simple.parser.ParseException {
		return commonServiceUtil.validMandatoryDocumentsForApplicant(demographicEntity);
	}

	private boolean compareUploadedDocListAndValidMandatoryDocList(List<String> availableDocs,
			List<String> validMandatoryDocForApplicant) {
		if (validMandatoryDocForApplicant.isEmpty()) {
			return false;
		} else {
			availableDocs.forEach(docCat -> validMandatoryDocForApplicant.remove(docCat));
			if (!validMandatoryDocForApplicant.isEmpty()) {
				return true;
			} else {
				return false;
			}
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
	public void updateApplicationStatusToIncomplete(DemographicEntity demographicEntity) {
		commonServiceUtil.updatePreRegistrationStatus(demographicEntity.getPreRegistrationId(),
				StatusCodes.APPLICATION_INCOMPLETE.getCode(), demographicEntity.getCreatedBy());
	}
}
