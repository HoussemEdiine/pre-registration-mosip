/* 
 * Copyright
 * 
 */
package io.mosip.preregistration.core.common.dto;

import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * This DTO class is used to define the values for document deletion.
 * 
 * @author Kishan Rathore
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class DocumentDeleteResponseDTO implements Serializable {
	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = 7070542323407937205L;

	/**
	 * Delete response Message
	 */
	private String message;
}