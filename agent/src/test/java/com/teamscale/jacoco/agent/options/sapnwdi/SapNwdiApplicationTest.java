package com.teamscale.jacoco.agent.options.sapnwdi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests for parsing the SAP NWDI application definition. */
public class SapNwdiApplicationTest {

	@Test
	public void testEmptyConfig() {
		assertThatThrownBy(() -> SapNwdiApplication.parseApplications(""))
				.hasMessage("Application definition is expected not to be empty.");
		assertThatThrownBy(() -> SapNwdiApplication.parseApplications(";"))
				.hasMessage("Application definition is expected not to be empty.");
	}

	@Test
	public void testIncompleteMarkerClassConfig() {
		assertThatThrownBy(() -> SapNwdiApplication.parseApplications(":alias"))
				.hasMessage("Marker class is not given for :alias!");
	}

	@Test
	public void testIncompleteProjectConfig() {
		assertThatThrownBy(() -> SapNwdiApplication.parseApplications("class:"))
				.hasMessage(
						"Application definition class: is expected to contain a marker class and project separated by a colon.");
	}

	@Test
	public void testSingleApplication() throws Exception {
		List<SapNwdiApplication> configuration = SapNwdiApplication.parseApplications("com.teamscale.test2.Bar:alias");
		assertThat(configuration).element(0).satisfies(application -> {
			assertThat(application.markerClass).isEqualTo("com.teamscale.test2.Bar");
			assertThat(application.teamscaleProject).isEqualTo("alias");
		});
	}

	@Test
	public void testMultipleApplications() throws Exception {
		List<SapNwdiApplication> configuration = SapNwdiApplication
				.parseApplications("com.teamscale.test1.Bar:alias; com.teamscale.test2.Bar:id");
		assertThat(configuration).element(0).satisfies(application -> {
			assertThat(application.markerClass).isEqualTo("com.teamscale.test1.Bar");
			assertThat(application.teamscaleProject).isEqualTo("alias");
		});
		assertThat(configuration).element(1).satisfies(application -> {
			assertThat(application.markerClass).isEqualTo("com.teamscale.test2.Bar");
			assertThat(application.teamscaleProject).isEqualTo("id");
		});
	}
}
