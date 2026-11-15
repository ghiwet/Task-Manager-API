# Case Studies

Two write-ups of real security/infrastructure problems found and fixed while hardening this
project's Kafka deployment (threat #32 — the broker had no authentication at all, so any pod on the
network could forge `task-events`). Written up here because both involved debugging through a
wrong assumption before finding the actual root cause, which is a more useful thing to be able to
talk about than the final diff.

## 1. The bootstrap credential that silently never got created

**The goal:** switch Kafka from PLAINTEXT to SASL/SCRAM, with the broker's SCRAM credentials seeded
directly into the metadata log at format time (`kafka-storage.sh format --add-scram`, KIP-900) —
no temporary unauthenticated listener, no chicken-and-egg bootstrap dance.

**What I expected:** run a one-shot `kafka-init` container that calls the image's own setup wrapper
to generate `server.properties` from the usual `KAFKA_*` env vars, then run `kafka-storage.sh format
--add-scram '...'` against it. Two commands, done.

**What actually happened:** the format succeeded, the broker started, and the SCRAM credentials
simply weren't there — no error, no warning, just an authentication failure the first time a real
client connected.

**Root cause:** the image's own setup wrapper (`kafka.docker.KafkaDockerWrapper setup`) doesn't just
generate config — it also *formats storage itself*, as an undocumented side effect, before my
explicit `--add-scram` command ever got to run. By the time my format call executed, the directory
was already formatted (without the credentials), and `kafka-storage.sh` silently no-ops on an
already-formatted directory unless you pass `--ignore-formatted` to force a loud failure instead —
which is how I actually found this, by making the second call fail loudly rather than succeed
quietly.

**The fix:** stop going through the image's wrapper at all for this step. `kafka-storage.sh format`
only needs the KRaft identity properties (`process.roles`, `node.id`, `controller.quorum.voters`,
`listeners`, `log.dirs`) to seed valid bootstrap metadata — not the full runtime config. A small
hand-written properties file with just those fields, formatted directly with `--add-scram`, sidesteps
the wrapper's side effect entirely.

**A second bug in the same feature, found the same way:** after fixing that, the broker still
couldn't see the credentials. This time the init container and the main broker container were
writing to *different* log directories — `KAFKA_LOG_DIRS` wasn't set, so each container fell back to
its own default path, and the two were never actually sharing the volume I'd mounted. Setting
`KAFKA_LOG_DIRS` explicitly in both containers' env (pointing them at the same mounted path) fixed
it — the volume mount alone wasn't enough; both sides needed to agree on *where* in it to look.

**Verified live, not just by the YAML looking right:** brought the real broker up, confirmed the
seeded credentials existed (`UserScramCredentialRecord` entries in the bootstrap metadata log),
confirmed an unauthenticated `kafka-topics.sh --list` hung/timed out against it, and confirmed the
same command with real SASL credentials succeeded.

## 2. A version bump that silently broke an internal API nobody was supposed to depend on

**The goal:** two Dependabot alerts (`lz4-java`, `snappy-java` — Kafka's own compression codec
dependencies) needed fixing. The "proper" fix — bump the thing that actually declares those versions,
rather than overriding two leaf libraries directly — was bumping `kafka.version` (a property Spring
Boot's BOM already exposes) to the one `kafka-clients` release that ships both patched together.

**What happened:** the dependency tree resolved exactly as expected. The full test suite did not —
every test using `@EmbeddedKafka` failed with:

```
NoSuchMethodError: 'kafka.server.metadata.KRaftMetadataCache kafka.server.BrokerServer.metadataCache()'
```

**Root cause:** `spring-kafka-test`'s `EmbeddedKafkaKraftBroker` calls a method directly on Kafka's
own internal `BrokerServer` class — not a published, stable API, just an implementation detail the
test-support library happens to reach into. Spring Boot pins `spring-kafka` and `kafka-clients`
together as a matched pair precisely because of couplings like this one; that internal method's
signature changed between the two Kafka minor versions, and the *test harness* (not the app code,
not even the Kafka client library itself) was compiled against the older shape.

**The decision, not just the fix:** bumping `kafka.version` on its own would "fix" two Dependabot
alerts while breaking the test suite's ability to verify anything Kafka-related — a worse trade. The
two leaf libraries are self-contained compression codecs with ordinary, stable public APIs and no
such internal coupling, so reverting to direct `dependencyManagement` overrides for just those two is
the actually-safer choice, even though it's less "pure" than bumping a single property. Documented
the reasoning in `pom.xml` itself, not just here, so a future "why not just bump kafka.version"
question has an answer on hand.

**Verified:** full suite green after the revert; confirmed via `dependency:tree` that the two leaf
libraries resolved to their patched versions while `kafka-clients` stayed on Boot's managed version.
