package io.mosip.preregistration.application.controller;

import static io.mosip.preregistration.application.constant.PreRegApplicationConstant.LOGGER_SESSIONID;
import static io.mosip.preregistration.application.constant.PreRegApplicationConstant.LOGGER_IDTYPE;
import static io.mosip.preregistration.application.constant.PreRegApplicationConstant.LOGGER_ID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.Errors;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.preregistration.application.dto.ApplicationRequestDTO;
import io.mosip.preregistration.application.dto.ApplicationResponseDTO;
import io.mosip.preregistration.application.dto.DeleteApplicationDTO;
import io.mosip.preregistration.application.service.ApplicationServiceIntf;
import io.mosip.preregistration.core.code.BookingTypeCodes;
import io.mosip.preregistration.core.common.dto.MainRequestDTO;
import io.mosip.preregistration.core.common.dto.MainResponseDTO;
import io.mosip.preregistration.core.config.LoggerConfiguration;
import io.mosip.preregistration.core.util.DataValidationUtil;
import io.mosip.preregistration.core.util.RequestValidator;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "lost-uin-controller", description = "Lost UIN Controller")

public class LostUINController {
	private Logger log = LoggerConfiguration.logConfig(LostUINController.class);

	/** The Constant CREATE_LOST_FORGOTTEN_UIN application. */
	private static final String LOST_UIN_CREATE_ID = "preregistration.lostuin.create";
	@Autowired
	private RequestValidator requestValidator;

	@Autowired
	ApplicationServiceIntf applicationService;

	/**
	 * Inits the binder.
	 *
	 * @param binder the binder
	 */
	@InitBinder
	public void initBinder(WebDataBinder binder) {
		binder.addValidators(requestValidator);
	}

	/**
	 * This Post API is use to create a new application with booking type as
	 * LOST_FORGOTTEN_UIN.
	 *
	 * @param jsonObject the json object
	 * @param errors     Errors
	 * @return List of response dto containing pre-id and group-id
	 */
	@PreAuthorize("hasAnyRole(@authorizedRoles.getPostapplications())")
	@PostMapping(path = "/applications/lostuin", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "addLostUinApplication", description = "Creates a new application with Booking Type as LOST_FORGOTTEN_UIN", tags = "lost-uin-controller")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "New application with booking type as LOST_FORGOTTEN_UIN successfully Created"),
			@ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(hidden = true))),
			@ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(hidden = true))),
			@ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(hidden = true))),
			@ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(hidden = true))) })
	public ResponseEntity<MainResponseDTO<ApplicationResponseDTO>> addLostUinApplication(
			@Validated @RequestBody(required = true) MainRequestDTO<ApplicationRequestDTO> jsonObject,
			@ApiParam(hidden = true) Errors errors) {
		log.info(LOGGER_SESSIONID, LOGGER_IDTYPE, LOGGER_ID,
				"In pre-registration LostUINController for createNewApplication with json object" + jsonObject);
		requestValidator.validateId(LOST_UIN_CREATE_ID, jsonObject.getId(), errors);
		DataValidationUtil.validate(errors, LOST_UIN_CREATE_ID);
		return ResponseEntity.status(HttpStatus.OK).body(applicationService.addLostOrUpdateApplication(jsonObject,
				BookingTypeCodes.LOST_FORGOTTEN_UIN.toString()));
	}

	/**
	 * This method is used to delete the application with booking type as
	 * LOST_FORGOTTEN_UIN
	 * 
	 * @param applicationId the applicationId
	 * @return the deletion status of application for a applicationId
	 */
	@PreAuthorize("hasAnyRole(@authorizedRoles.getDeleteapplications())")
	@DeleteMapping(path = "/applications/lostuin/{applicationId}", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "deleteLostUinApplication", description = "Delete application with booking type LOST_FORGOTTEN_UIN.", tags = "lost-uin-controller")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Deletion of application is successfully"),
			@ApiResponse(responseCode = "204", description = "No Content"),
			@ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(hidden = true))),
			@ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(hidden = true))), })
	public ResponseEntity<MainResponseDTO<DeleteApplicationDTO>> deleteLostUinApplication(
			@PathVariable("applicationId") String applicationId) {
		log.info(LOGGER_SESSIONID, LOGGER_IDTYPE, LOGGER_ID,
				"In pre-registration LostUINController for deleteApplication with preId " + applicationId);
		return ResponseEntity.status(HttpStatus.OK).body(applicationService.deleteLostOrUpdateApplication(applicationId,
				BookingTypeCodes.LOST_FORGOTTEN_UIN.toString()));
	}
}