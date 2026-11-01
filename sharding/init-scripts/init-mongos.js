// Run this AFTER config server and all 3 shard replica sets are initiated
// and have finished electing a PRIMARY (wait ~15-20 seconds after rs.initiate()).

// 1. Add all shards to the cluster
sh.addShard("shard1rs/shard1svr:27018");
sh.addShard("shard2rs/shard2svr:27018");
sh.addShard("shard3rs/shard3svr:27018");

// 2. Enable sharding on the College database
sh.enableSharding("College");

// 3. Create an index on the shard key (required before sharding the collection)
db = db.getSiblingDB("College");
db.Student.createIndex({ RollNo: 1 });

// 4. Shard the Student collection using RollNo as the shard key
sh.shardCollection("College.Student", { RollNo: 1 });

// 5. Manually pre-split the RollNo range into 3 chunks and distribute
//    one to each shard. This is necessary because our dataset (~1MB)
//    is far too small to trigger MongoDB's automatic chunk splitting
//    (default threshold is 128MB), so without this step all data
//    would stay on a single shard.
sh.splitAt("College.Student", { RollNo: 3334 });
sh.splitAt("College.Student", { RollNo: 6667 });

sh.moveChunk("College.Student", { RollNo: 1 },    "shard1rs");
sh.moveChunk("College.Student", { RollNo: 3334 }, "shard2rs");
sh.moveChunk("College.Student", { RollNo: 6667 }, "shard3rs");

// 6. Confirm setup
sh.status();