#JsonDisplay

This is the code for a an Android app that I wrote as a learning project. 

The brief was to write an app that:-
1) asks the user to set a 4-6 digit pin, but disallowing patterns which might be insecure.
2) downloads some Json formatted data via http from a url.
3) parses the json and stores the data securely.
4) presents the data to authenticated users using the android ui.
5) maintains data security in the light of possible life-cycle events imposed by the android system.

The app does all of the above; and as a bonus, while I was writing it I found a typo in the open source 
encryption library that I used, which is now fixed by the PR that I submitted. =)
