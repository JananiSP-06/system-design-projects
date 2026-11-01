db = db.getSiblingDB("College");

var depts = ["CSE", "ECE", "MECH", "CIVIL", "IT"];
var docs = [];

for (var i = 1; i <= 10000; i++) {
  docs.push({
    RollNo: i,
    Name: "Student" + i,
    Dept: depts[i % depts.length],
    Year: (i % 4) + 1,
    Marks: Math.floor(Math.random() * 41) + 60   // marks between 60-100
  });

  // insert in batches of 1000 for speed
  if (docs.length === 1000) {
    db.Student.insertMany(docs);
    docs = [];
  }
}
if (docs.length > 0) {
  db.Student.insertMany(docs);
}

print("Inserted 10000 student documents.");
db.Student.getShardDistribution();