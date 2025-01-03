/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.targets;

import java.time.Duration;
import java.util.List;

import io.cryostat.ConfigProperties;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.libcryostat.JvmIdentifier;
import io.cryostat.recordings.RecordingHelper;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class TargetUpdateJob implements Job {

    @Inject Logger logger;
    @Inject TargetConnectionManager connectionManager;
    @Inject RecordingHelper recordingHelper;

    @ConfigProperty(name = ConfigProperties.CONNECTIONS_FAILED_TIMEOUT)
    Duration connectionTimeout;

    @Override
    @Transactional
    public void execute(JobExecutionContext context) throws JobExecutionException {
        List<Target> targets;
        Long targetId = (Long) context.getJobDetail().getJobDataMap().get("targetId");
        if (targetId != null) {
            targets = List.of(Target.getTargetById(targetId));
        } else {
            targets = Target.<Target>find("#Target.unconnected").list();
        }

        if (targets.size() == 1) {
            updateTarget(targets.get(0));
        } else {
            targets.forEach(
                    t -> Infrastructure.getDefaultExecutor().execute(() -> updateTargetTx(t.id)));
        }
    }

    private void updateTargetTx(long id) {
        QuarkusTransaction.requiringNew().run(() -> updateTarget(Target.getTargetById(id)));
    }

    private void updateTarget(Target target) {
        try {
            target.jvmId =
                    connectionManager
                            .executeConnectedTaskUni(target, JFRConnection::getJvmIdentifier)
                            .map(JvmIdentifier::getHash)
                            .await()
                            .atMost(connectionTimeout);
        } catch (Exception e) {
            target.jvmId = null;
            target.persist();
            throw e;
        }
        target.activeRecordings = recordingHelper.listActiveRecordings(target);
        target.persist();
    }
}
