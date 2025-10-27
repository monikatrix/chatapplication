document.getElementById("signupForm").addEventListener("submit", async (e) => {
  e.preventDefault();

  const username = document.getElementById("username").value.trim();
  const email = document.getElementById("email").value.trim();
  const password = document.getElementById("password").value.trim();

  const response = await fetch("signup", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, email, password }),
  });

  if (response.ok) {
    alert("Registration successful! Please sign in.");
    window.location.href = "signin.html";
  } 
  else {
    const text = await response.text();
    alert("Error: " + text);
  }
});
