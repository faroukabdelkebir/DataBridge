package wevioo.se.monitoringms.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import wevioo.se.monitoringms.config.CacheConfig;
import wevioo.se.monitoringms.service.SsePublisher;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StreamController.class)
@Import(CacheConfig.class)
class StreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SsePublisher ssePublisher;

    @Test
    void streamJobsSubscribesTheCallerAndOpensAnEventStream() throws Exception {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        when(ssePublisher.subscribe()).thenReturn(emitter);

        var result = mockMvc.perform(get("/api/v1/stream/jobs"))
                .andExpect(request().asyncStarted())
                .andReturn();

        emitter.complete();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

        verify(ssePublisher).subscribe();
    }
}
