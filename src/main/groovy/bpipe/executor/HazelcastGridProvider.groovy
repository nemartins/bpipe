/*
 * Copyright (c) Murdoch Childrens Research Institute and Contributers
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package bpipe.executor

import java.util.concurrent.ExecutorService
import groovy.util.logging.Log

import bpipe.Config
import bpipe.EventManager
import bpipe.PipelineEvent
import bpipe.PipelineEventListener

import com.hazelcast.client.ClientConfig
import com.hazelcast.client.HazelcastClient
import com.hazelcast.core.HazelcastInstance

/**
 *  Instantiate a Hazelcast client connecting to a running grid.
 *  <p>
 *  By default the grid is supposed to available on the localhost.
 *  To provide a different host address(es) use provide the configuration
 *  property {@code hazelcast.client.addresses} in the {@code bpipe.config} file
 *  <p>
 *  For example:
 *  <code>
 *  executor="hazelcast"
 *  hazelcast.client.addresses = ["10.90.0.1", "10.90.0.2:5702"]
 *  </code>
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@Log
class HazelcastGridProvider implements ExecutorServiceProvider {

    @Lazy
    HazelcastInstance client = {

        def addresses = Config.userConfig.getOrDefault("hazelcast.client.addresses", "localhost")
        log.info("Connecting Hazelcast grid to addresses: ${addresses}")

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.addAddress( addresses );
        def result = HazelcastClient.newHazelcastClient(clientConfig);
        log.info("Hazelcast grid touched")

        /*
        * register the shutdown event
        */
        EventManager.instance.addListener(PipelineEvent.FINISHED, new PipelineEventListener() {
            @Override
            void onEvent(PipelineEvent eventType, String desc, Map<String, Object> details) {
                log.info("Shutting down Hazelcast")
                result.getLifecycleService().shutdown()
            }
        })

        return result

    }()


    def getName() { "Hazelcast" }

    @Override
    ExecutorService getExecutor() {

        client.getExecutorService()

    }
}
