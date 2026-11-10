# Chapter 15 video clips

The video questions of the driving theory test. Each clip belongs to one question, and the
question text and its answer live in `content/theory/video-clips.json`, not here.

Clips arrive either as a link, which is fetched and re-encoded here, or uploaded straight into
this folder on GitHub (Add file → Upload files).

Naming: lowercase, words separated by hyphens, `.mp4`, named after what the clip shows — for
example `zebra-crossing.mp4`. A name with spaces or capitals still uploads; it is renamed here
before the question is written, because the object path goes into the database as it is. A clip's
name must not change once its questions are published: the path is what the row points at.

The clips are uploaded to the public `question-media` bucket by
`scripts/media/upload_question_media.sh` in the content workflow, under the same relative path
(`theory/clips/<file>.mp4`).

Keep each clip small: a phone downloads the whole chapter, and the app does not save a file over
8 MB. These are re-encoded with `ffmpeg -c:v libx264 -crf 31 -preset slow -c:a aac -b:a 64k`,
which halves them without blurring the road markings a question may turn on.
