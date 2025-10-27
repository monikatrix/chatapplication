document.addEventListener("DOMContentLoaded", () => {
  const username = localStorage.getItem("username");
  const email = localStorage.getItem("email");
  const chatBox = document.getElementById("chatArea");
  const messageInput = document.getElementById("message");
  const sendBtn = document.getElementById("sendBtn");
  const modal = document.getElementById("groupModal");
  const overlay = document.getElementById("modalOverlay");
  
  if (!username || !email) {
    window.location.replace = "signin.html";
	return;
  }

  if (window.chatSocket && window.chatSocket.readyState === WebSocket.OPEN) {
     console.log("WebSocket already open. Skipping new connection.");
     return;
   }
  const socket = new WebSocket(`ws://localhost:8080/chat-application/chat/${username}`);
	
  socket.onopen = () => 
	{
		console.log("Connected to chat server");
		appendSystemMessage(`Connected as ${username}`);
		requestGroupList();
	}
  socket.onmessage = (event) => handleIncoming(JSON.parse(event.data));
  socket.onerror = (err) => appendSystemMessage("Connection error: " + err.message);
  socket.onclose = () => appendSystemMessage("Disconnected from server");

  sendBtn.onclick = sendMessage;
  messageInput.onkeypress = (e) => {
      if (e.key === "Enter") sendMessage();
    };

  function sendMessage() {
    const content = messageInput.value.trim();
    if (!content) return;

    const type = document.getElementById("messageType").value;
    const recipient = document.getElementById("recipient").value.trim();
    const selectedGroup = document.getElementById("groupSelect").value;

    let msg = { type, content, senderEmail: email };

    if (type === "private") {
      if (!recipient) return alert("Enter recipient username");
      msg.to = recipient;
    } else if (type === "group") {
      if (!recipient) return alert("Enter comma-separated recipients");
      msg.to = recipient;
    } else if (type === "group-message") {
      if (!selectedGroup) return alert("Select a group to send message");
      msg.groupName = selectedGroup;
    }
	
	if(socket.readyState === WebSocket.OPEN){
	    socket.send(JSON.stringify(msg));
	    messageInput.value = "";
	    //appendMessage(username, content, nowTime(), "me");
	}
	else{
		appendSystemMessage("Connection closed. Unable to send message.");
	}
  }

  function handleIncoming(msg) {
    const { type, sender, content, timestamp } = msg;
	
	if(msg.type==="group-list"){
		updateGroupDropdown(msg.groups);
		return;
	}

    if (type === "system"){
		if(content.includes("Group '")){
			const match = content.match(/Group '(.+?)'/);
			if(match){
				const groupName = match[1].trim();
				const groupSelect = document.getElementById("groupSelect");
				groupSelect.style.display = "inline-block";
				const opt = document.createElement("option");
				opt.value = groupName;
				opt.textContent = groupName;
				groupSelect.appendChild(opt);
			}
		}
		appendSystemMessage(content, timestamp);
	}
    else appendMessage(sender || "Unknown", content, timestamp, sender === username ? "me" : "received");
  }

  function appendMessage(sender, content, time, cls) {
    const div = document.createElement("div");
    div.className = `message ${cls}`;
	
	const isMe = cls === "me"
	const displayName = isMe ? "You":sender || "Unknown";
    div.innerHTML = `<b>${displayName}:</b> ${content} <span class="timestamp">${time || ""}</span>`;
    chatBox.appendChild(div);
    chatBox.scrollTop = chatBox.scrollHeight;
  }

  function appendSystemMessage(content, time) {
    const div = document.createElement("div");
    div.className = "message system";
    div.innerHTML = `${content} ${time ? `<span class="timestamp">${time}</span>` : ""}`;
    chatBox.appendChild(div);
    chatBox.scrollTop = chatBox.scrollHeight;
  }

  function nowTime() {
    return new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  }
  
  function requestGroupList(){
	socket.send(JSON.stringify({type:"list-groups"}));
  }
  
  function updateGroupDropdown(groups){
	const select = document.getElementById("groupSelect");
	select.innerHTML = "<option value=''>-- Select Group --</option>";
	if(!groups || groups.length===0) return;
	
	select.style.display = "inline-block";
	groups.forEach((g) => {
		const opt = document.createElement("option");
		opt.value = g;
		opt.textContent = g;
		select.appendChild(opt);
	}); 
  }
  
  document.getElementById("logoutBtn").onclick=()=>{
	localStorage.clear();
	document.cookie = "username=; path=/; max-age=0";
	document.cookie = "authToken=; path=/; max-age=0";
	alert("You have logged out!");
	if(socket.readyState === WebSocket.OPEN){
		socket.close();
	}
	window.location.href = "signin.html";
  }
  
  document.getElementById("createGroupBtn").onclick = () => {
          modal.style.display = "block";
          overlay.style.display = "block";
      };

      document.getElementById("closeModal").onclick = () => {
          modal.style.display = "none";
          overlay.style.display = "none";
      };

      document.getElementById("saveGroup").onclick = () => {
          const name = document.getElementById("groupName").value.trim();
          const members = document.getElementById("groupMembers").value.trim().split(",").map(m => m.trim()).filter(m => m.length > 0);

          if (!name || members.length === 0) return alert("Enter group name and at least one member!");

          const msg = { type: "create-group", groupName: name, members: members };
          socket.send(JSON.stringify(msg));

          modal.style.display = "none";
          overlay.style.display = "none";
          document.getElementById("groupName").value = "";
          document.getElementById("groupMembers").value = "";
      };
});
