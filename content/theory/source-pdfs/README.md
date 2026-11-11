# Chapter PDFs, for reading only

Drop a chapter's PDF here and the questions and pictures in it that the course does not have yet
are added to that chapter. Nothing is ever removed: a chapter in the app keeps every question it
already has, whatever the PDF holds.

`car/` is the one the course is built from: the app teaches the car test (category B).
`motorcycle/` holds the same chapters of the motorcycle test, kept apart because the two ask
different things - a motorcycle chapter says "you're riding", asks about helmets and pillion
passengers, and sometimes gives a different answer to the same situation.

Upload on GitHub (Add file → Upload files) into the folder for that category, on the working
branch, not on `main`. GitHub takes up to 25 MB per file through the browser, which every
chapter is under. Several at a time is fine; upload them in batches if the browser struggles.

**These PDFs do not stay in the repository.** They are read here, the questions and the pictures
they carry are written into `content/theory/*.json` and `content/media/theory/`, and the branch
is then rebuilt without them, so the repository is not carrying tens of megabytes of source
material that nothing reads again. Only the extracted questions and pictures are merged.
