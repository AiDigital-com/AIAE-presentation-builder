package com.aidigital.reportconstructor.externalservices.google.drive;

import com.aidigital.reportconstructor.externalservices.google.GoogleProperties;
import com.aidigital.reportconstructor.externalservices.google.GoogleCallTimer;
import com.aidigital.reportconstructor.observability.external.ExternalCallTimer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriveCopierTest {

	@Test
	void shouldCreateFolderInsideTargetFolderWhenConfiguredTest() throws IOException {
		// Given:
		GoogleProperties props = new GoogleProperties();
		props.setSlidesTargetFolderId(" reports-folder ");
		DriveCopier copier = new DriveCopier(props, new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));
		Drive drive = mock(Drive.class, RETURNS_DEEP_STUBS);
		Drive.Files.Create create = mock(Drive.Files.Create.class, RETURNS_DEEP_STUBS);
		ArgumentCaptor<File> folder = ArgumentCaptor.forClass(File.class);
		when(drive.files().create(folder.capture())).thenReturn(create);
		when(create.setFields("id").setSupportsAllDrives(true).execute().getId()).thenReturn("folder-1");

		// When:
		String id = copier.createFolder(drive, "Charts — Campaign");

		// Then:
		assertThat(id).isEqualTo("folder-1");
		assertThat(folder.getValue().getName()).isEqualTo("Charts — Campaign");
		assertThat(folder.getValue().getMimeType()).isEqualTo("application/vnd.google-apps.folder");
		assertThat(folder.getValue().getParents()).isEqualTo(List.of("reports-folder"));
	}

	@Test
	void shouldCreateFolderWithoutParentWhenTargetFolderBlankTest() throws IOException {
		// Given:
		GoogleProperties props = new GoogleProperties();
		props.setSlidesTargetFolderId("");
		DriveCopier copier = new DriveCopier(props, new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));
		Drive drive = mock(Drive.class, RETURNS_DEEP_STUBS);
		Drive.Files.Create create = mock(Drive.Files.Create.class, RETURNS_DEEP_STUBS);
		ArgumentCaptor<File> folder = ArgumentCaptor.forClass(File.class);
		when(drive.files().create(folder.capture())).thenReturn(create);
		when(create.setFields("id").setSupportsAllDrives(true).execute().getId()).thenReturn("folder-2");

		// When:
		String id = copier.createFolder(drive, "Breakdown charts — Campaign");

		// Then:
		assertThat(id).isEqualTo("folder-2");
		assertThat(folder.getValue().getParents()).isNull();
		verify(drive.files()).create(folder.getValue());
	}
}
