package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.drive.Drive;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Drive folder creation and template spreadsheet copies for the chart engine.
 */
@Component
@RequiredArgsConstructor
public class DriveCopier {

	private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
	private static final String SPREADSHEET_MIME_TYPE = "application/vnd.google-apps.spreadsheet";

	private final GoogleProperties props;

	/** Times every Drive call on the shared external-call timer. */
	private final GoogleCallTimer googleCalls;

	/**
	 * Creates a Google Drive folder (shared-drive aware) to hold the per-report chart spreadsheet copies.
	 * The folder is placed inside {@code external.google.slides-target-folder-id} when that is set, so the
	 * chart copies land next to the generated deck instead of in the service account's own Drive, which
	 * has no storage quota; when it is blank the folder keeps Drive's default location.
	 *
	 * @param drive authenticated Google Drive API client used to issue the create call
	 * @param name  display name for the new folder
	 * @return the Drive file id of the newly created folder
	 * @throws IOException if the Drive create request fails
	 */
	public String createFolder(Drive drive, String name) throws IOException {
		com.google.api.services.drive.model.File folder = new com.google.api.services.drive.model.File()
				.setName(name)
				.setMimeType(FOLDER_MIME_TYPE);
		String parentId = props.getSlidesTargetFolderId();
		if (parentId != null && !parentId.isBlank()) {
			folder.setParents(List.of(parentId.trim()));
		}
		return googleCalls.execute(drive.files().create(folder).setFields("id").setSupportsAllDrives(true)).getId();
	}

	/**
	 * Copies a chart-template spreadsheet, optionally placing the copy inside the given folder.
	 *
	 * @param drive    authenticated Google Drive API client used to issue the copy call
	 * @param fileId   Drive file id of the source chart-template spreadsheet to duplicate
	 * @param name     display name for the resulting copy
	 * @param folderId Drive folder id to set as the copy's parent; when null or empty the copy keeps the default
	 *                    location
	 * @return the Drive file id of the newly created copy
	 * @throws IOException if the Drive copy request fails
	 */
	public String copyFile(Drive drive, String fileId, String name, String folderId) throws IOException {
		com.google.api.services.drive.model.File copy = new com.google.api.services.drive.model.File().setName(name);
		if (folderId != null && !folderId.isEmpty()) {
			copy.setParents(List.of(folderId));
		}
		return googleCalls.execute(drive.files().copy(fileId, copy).setFields("id").setSupportsAllDrives(true)).getId();
	}

	/**
	 * Creates an empty Google Sheets spreadsheet, inside the given folder when one is given (a service account has
	 * no Drive quota of its own, so its files must live in a shared folder).
	 *
	 * @param drive    authenticated Google Drive API client used to issue the create call
	 * @param name     display name for the new spreadsheet
	 * @param folderId Drive folder id to set as its parent; when null or blank it keeps the default location
	 * @return the Drive file id of the new spreadsheet
	 * @throws IOException if the Drive create request fails
	 */
	public String createSpreadsheet(Drive drive, String name, String folderId) throws IOException {
		com.google.api.services.drive.model.File sheet = new com.google.api.services.drive.model.File()
				.setName(name)
				.setMimeType(SPREADSHEET_MIME_TYPE);
		if (folderId != null && !folderId.isBlank()) {
			sheet.setParents(List.of(folderId));
		}
		return googleCalls.execute(drive.files().create(sheet).setFields("id").setSupportsAllDrives(true)).getId();
	}
}
