# Chapter 15 video clips

The video questions of the driving theory test. Each clip belongs to one question, and the
question text and its answer live in `content/theory/video-clips.json`, not here.

Upload the clips straight into this folder on GitHub (Add file → Upload files).

Naming: lowercase, words separated by hyphens, `.mp4`, named after the question — for example
`white-diagonal-stripes.mp4`. A name with spaces or capitals still uploads; it is renamed here
before the question is written, because the object path goes into the database as it is.

The clips are uploaded to the public `question-media` bucket by
`scripts/media/upload_question_media.sh` in the content workflow, under the same relative path
(`theory/clips/<file>.mp4`).

Keep each clip small: a phone downloads the whole chapter. Re-encode anything much over about
2 MB (720p, H.264, AAC) before it is committed.
