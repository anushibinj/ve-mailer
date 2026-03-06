package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.repository.FilterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FilterController.class)
@AutoConfigureMockMvc(addFilters = false)
class FilterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FilterRepository filterRepository;

    @Test
    void testGetFilters() throws Exception {
        Filter f = new Filter();
        f.setId(UUID.randomUUID());
        f.setTitle("Urgent Tickets");
        f.setDescription("Show urgent");
        f.setQuery("status=urgent");

        when(filterRepository.findAll()).thenReturn(Arrays.asList(f));

        mockMvc.perform(get("/api/v1/filters")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Urgent Tickets"))
                .andExpect(jsonPath("$[0].query").value("status=urgent"));
    }
}
