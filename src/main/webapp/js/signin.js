/*document.addEventListener("DOMContentLoaded",()=>{
	const username = localStorage.getItem("username");
	const email = localStorage.getItem("email");
	  if(username && email){
		window.location.href = "chat.html";
	  }
});*/

document.getElementById("signinForm").addEventListener("submit", async (e) => {
  e.preventDefault();

  const email = document.getElementById("email").value.trim();
  const password = document.getElementById("password").value.trim();

  try{
	const response = await fetch("signin", {
	    method: "POST",
	    headers: { "Content-Type": "application/json" },
	    body: JSON.stringify({ email, password }),
		redirect:"follow"
	  });
	  
	  if(response.redirected){
		window.location.href = response.url;
		return;
	  }

	  
	  if(response.ok){
		alert("Login successful!");
		window.location.href="chat.html";
		
	  }
	  else{
		const errorText = await response.text();
		 alert(errorText || "Invalid email or password");
	  }
  }
  catch(err){
	console.error("Signin error", err);
	alert("Something went wrong. Please try again later.");
  }
});
