function formatTime(dateString) {
    if (!dateString) return "";
	const safeString = dateString.replace(" ","T");
    const date = new Date(safeString);
    if (isNaN(date.getTime())) return "";  
    const hours = date.getHours().toString().padStart(2, "0");
    const minutes = date.getMinutes().toString().padStart(2, "0");
    return `${hours}:${minutes}`;
}

document.addEventListener("DOMContentLoaded", () => {
	const username = getCookie("username");
	  if(!username){
		alert("Username not found. Please log in again.");
		window.location.href = "siginin.html";
		return;
	  }
	  
	  const socket = new WebSocket(`ws://localhost:8080/chat-application/chat/${username}`);

	  const privateList = document.getElementById("privateList");
	  const groupList = document.getElementById("groupList");
	  const chatArea = document.getElementById("chatArea");
	  const messageInput = document.getElementById("messageInput");
	  const sendBtn = document.getElementById("sendBtn");
	  const chatName = document.getElementById("chatName");
	  const lastSeen = document.getElementById("lastSeen");
	  const createGroupBtn = document.getElementById("createGroupBtn");
	  const addMemberBtn = document.getElementById("addMemberBtn");
	  const removeMemberBtn = document.getElementById("removeMemberBtn");
		  
	  const privateTab = document.getElementById("privateTab");
	  const groupTab = document.getElementById("groupTab");
	  
	  let currentChat = null;
	  let currentType = null;
	  let allUsers = [];
	  let onlineUsers = [];
	  
	  privateTab.addEventListener("click", () => {
	    privateTab.classList.add("active");
	    groupTab.classList.remove("active");
	    privateList.classList.remove("hidden");
	    groupList.classList.add("hidden");
		addMemberBtn.style.display = "none";
		removeMemberBtn.style.display = "none";
	  });

	  groupTab.addEventListener("click", () => {
	    groupTab.classList.add("active");
	    privateTab.classList.remove("active");
	    groupList.classList.remove("hidden");
	    privateList.classList.add("hidden");
		addMemberBtn.style.display = "inline-block";
		removeMemberBtn.style.display = "inline-block";
	  });
	  
	  addMemberBtn.onclick = addMemberToGroup;
	  removeMemberBtn.onclick = removeMemberFromGroup;
	  
	  socket.onopen = () => {
	      appendSystemMessage(`Connected as ${username}`);
		  socket.send(JSON.stringify({type: "get-all-users"}));
	      socket.send(JSON.stringify({type: "list-groups"}));
	    };

	    socket.onmessage = (event) => {
			const msg = JSON.parse(event.data);
			
			if(msg.type === "user-status-update"){
				updateUserStatus(msg.username, msg.status, msg.lastSeen);
				return;
			}
			handleIncoming(msg);
		}
	    socket.onclose = () => appendSystemMessage("Disconnected from server");
	    socket.onerror = (err) => appendSystemMessage("Error: " + err.message);
	  
	let email = null;

	fetch('/chat-application/signin')
	  .then(res => res.json())
	  .then(data => {
	    email = data.email;
	    console.log("Session email:", email);
	    if (!email) {
	      alert("Session expired. Please sign in again.");
	      window.location.href = "signin.html";
	    }
	  })
	  .catch(() => {
	    alert("Unable to retrieve session info. Please sign in again.");
	    window.location.href = "signin.html";
	  });

	  function getCookie(name){
		const value = `; ${document.cookie}`;
		const parts = value.split(`; ${name}=`);
		if(parts.length==2){
			return parts.pop().split(';').shift();
		}
		return null;
	  }
  
  	function handleIncoming(msg) {
		const { type, sender, content, timestamp } = msg;
    
		switch(type){
			case "system":
				appendSystemMessage(content);
				break;
				
			case "all-users":
				allUsers = msg.users;
				renderUserList(allUsers);
				break;
				
			case "online-users":
				onlineUsers = msg.users;
				renderUserList(allUsers);
				break;
				
			case "user-status-update":
				updateUserStatus(msg.username, msg.status, msg.lastSeen);
				break;
				
			case "group-list":
				renderGroupList(msg.groups);
				break;
				
			case "private":
				if(currentType === "private" && (sender === currentChat || sender === username)){
					appendMessage(sender, content, timestamp, sender===username);					
				}
				break;
				
			case "group-message":
				if(currentType === "group" && msg.groupName === currentChat){
					appendMessage(sender, content, timestamp, sender===username);					
				}
				break;
				
			case "private-history":
			case "group-history":
				loadHistory(msg.messages);
				break;
				
			default:
				console.warn("Unknown message type:",msg);
		}
	}


  let lastMessageDate = null;
  function appendMessage(sender, content, time, isMine, messageId=null) {
	if(lastMessageDate !== getDateLabel(time)){
		appendDateSeparator(getDateLabel(time));
		lastMessageDate = getDateLabel(time);
	}
	
    const div = document.createElement("div");
    div.className = `message ${isMine ? "me":"received"}`;
	div.innerHTML = `
	     <div class="bubble ${isMine ? "mine" : "theirs"}">
	       ${!isMine ? `<span class="sender">${sender}</span>` : ""}
	       <span class="content">${content}</span>
		   ${isMine ? `<span class="delete-btn" data-id="${messageId}" title="Delete>🗙</span>`:""}
		   <span class="time">${formatTime(time)}</span>
	     </div>
	   `;
	   const deleteBtn = div.querySelector(".delete-btn");
	     if (deleteBtn) {
	       deleteBtn.onclick = () => {
			const msgId = deleteBtn.dataset.id;
			if(!msgId){
				console.error("Message ID missing for delete");
				return;
			}
	         if (confirm("Delete this message permanently?")) {
	           if (currentType === "group") deleteGroupMessage(messageId);
	           else deletePrivateMessage(messageId);
	           div.remove();
	         }
	       };
		 }

	 chatArea.appendChild(div);
	 chatArea.scrollTop = chatArea.scrollHeight;	
  }
  
  function deletePrivateMessage(messageId){
	socket.send(JSON.stringify({
		type:"delete-private-message",
		messageId
	}))
  }
  
  function deleteGroupMessage(messageId) {
         socket.send(JSON.stringify({ 
			type: "delete-group-message",
			groupName: currentChat, 
			messageId }));
   }
  function appendDateSeparator(label) {
    const separator = document.createElement("div");
    separator.className = "date-separator";
    separator.textContent = label;
    chatArea.appendChild(separator);
  }


  function appendSystemMessage(content) {
    const div = document.createElement("div");
    div.className = "system-message";
    div.innerHTML = content;
	chatArea.appendChild(div);
	chatArea.scrollTop = chatArea.scrollHeight;
  }

  function loadHistory(messages){
	chatArea.innerHTML = "";
	if(!messages || messages.length===0){
		appendSystemMessage("No messages found.");
		return;
	}
	
	lastMessageDate = null;
	messages.forEach(m=> {
		appendMessage(m.sender_username, m.message, m.timestamp, m.sender_username === username,m.Id);
	});
  }

  
  function renderUserList(users) {
  	privateList.innerHTML = "";
  	  if (!users || users.length === 0) return;

  	  users.forEach((u) => {
  	    if (u.username !== username) {
		  const isOnline = onlineUsers.includes(u.username) || u.isOnline;
  	      const div = document.createElement("div");
  	      div.className = "user-item";
		  div.innerHTML = `
		            <span class="status-dot ${isOnline ? "online" : "offline"}"></span>
		            <span class="user-name">${u.username}</span>
		            <span class="last-seen">${isOnline ? "Online" : "Last seen " + u.lastSeen}</span>
		          `;
		  div.onclick = () => openPrivateChat(u);
  	      privateList.appendChild(div);
  	    }
  	  });
    }

	function renderGroupList(groups) {
		groupList.innerHTML = "";
		if(!groups || groups.length === 0) return;
		
	    groups.forEach((g) => {
  	      const div = document.createElement("div");
  	      div.className = "group-item";
  	      div.textContent = g;
		  div.onclick = () => openGroupChat(g);
  	      groupList.appendChild(div);
	  	 });
	 }

  	function openPrivateChat(user){
		currentChat = user.username;
		currentType = "private";
		chatName.textContent = user.username;
		lastSeen.textContent = user.isOnline ? "Online" : `Last seen ${user.lastSeen}`;
		chatArea.innerHTML = "";
		addMemberBtn.style.display = "none";
		removeMemberBtn.style.display = "none";
		socket.send(JSON.stringify({type: "get-private-history", with:user.username}));
	}
	
	function openGroupChat(groupName) {
	    currentChat = groupName;
	    currentType = "group";
	    chatName.textContent = groupName;
		lastSeen.textContent = "";
	    chatArea.innerHTML = "";
		addMemberBtn.style.display = "inline-block";
		removeMemberBtn.style.display = "inline-block";
	    socket.send(JSON.stringify({ type: "get-group-history", groupName }));
	  }
	 
	function addMemberToGroup(){
		if(currentType!=="group"){
			alert("You can only add members inside a group chat!");
			return;
		}
		const newMember = prompt("Enter the username of the user to add:");
		if(!newMember) return;
		
		socket.send(JSON.stringify({
			type: "add-group-member",
			groupName: currentChat,
			newMember
		}));
		alert(`Requested to add ${newMember} to ${currentChat}`);
	}
	
	function removeMemberFromGroup() {
	    if (currentType !== "group") {
	        alert("You can only remove members inside a group chat!");
	        return;
	    }
	    const member = prompt("Enter the username to remove from group:");
	    if (!member) return;

	    socket.send(JSON.stringify({
	        type: "remove-group-member",
	        groupName: currentChat,
	        member
	    }));
	    alert(`Requested to remove ${member} from ${currentChat}`);
	}

	function deleteGroupMessage(messageId) {
	    socket.send(JSON.stringify({
	        type: "delete-group-message",
	        groupName: currentChat,
	        messageId
	    }));
	}
	
	
	function updateUserStatus(username, status, lastSeenTime)
	{
		const items = document.querySelectorAll(".user-item");
		items.forEach((item) =>{
			const name = item.querySelector(".user-name").textContent;
			if(name === username){
				const dot = item.querySelector(".status-dot");
				const lastSeenEl = item.querySelector(".last-seen");
				if(status === "online"){
					dot.classList.add("online");
					dot.classList.remove("offline");
					lastSeenEl.textContent = "Online";
				}
				else{
					dot.classList.remove("online");
					dot.classList.add("offline");
					lastSeenEl.textContent = lastSeenTime
						? `Last seen ${lastSeenTime}`
						: "Offline";
				}
			}
		});
		
		if(currentChat===username && currentType ==="private"){
			lastSeen.textContent = 
				status === "online"
					? "Online"
					:lastSeenTime
					? `Last seen ${lastSeenTime}`
					: "Offline";			
		}
	}
	  
	
	 function sendMessage(){
		const content = messageInput.value.trim();
		if(!content || !currentChat || !currentChat) return;
		
		const msg = 
			currentType === "private"
				? {type: "private", to: currentChat, content}
				: {type: "group-message", groupName: currentChat, content}
		
		socket.send(JSON.stringify(msg));
		messageInput.value = "";		
	 }
	 
	 sendBtn.onclick = sendMessage;
	messageInput.onkeypress = (e) => {
	   if (e.key === "Enter") sendMessage();
	 };
	 	 
	createGroupBtn.onclick = () =>{
		const groupName = prompt("Enter Group Name:");
		if(!groupName || !groupName.trim()) return;
		
		const memberSelection = onlineUsers.filter((u) => u !== username);
		if(memberSelection.length === 0){
			alert("No other online users available to add.");
			 return;
		}
		
		let members = [];
		memberSelection.forEach((u) => {
			if(confirm(`Add ${u} to group '${groupName}'?`)){
				members.push(u);
			}
		});
		
		members.push(username);
		
		socket.send(
			JSON.stringify({
				type: "create-group",
				groupName,
				members,
			})
		);
		alert(`Group '${groupName}' created successfully!`);
	}
	  
	  document.getElementById("logoutBtn").onclick = () =>{
		window.location.href = "http://localhost:8080/chat-application/logout";
	  };
	  
	  function getDateLabel(dateString) {
		const safeString = dateString.replace(" ","T");
		const date = new Date(safeString);
		if(isNaN(date.getTime())) return "";
		
	    const today = new Date();
	    const yesterday = new Date();
	    yesterday.setDate(today.getDate() - 1);

	    const sameDay = (d1, d2) =>
	      d1.getDate() === d2.getDate() &&
	      d1.getMonth() === d2.getMonth() &&
	      d1.getFullYear() === d2.getFullYear();

	    if (sameDay(date, today)) return "Today";
	    if (sameDay(date, yesterday)) return "Yesterday";
	    return date.toLocaleDateString([], {
	      day: "numeric",
	      month: "short",
	      year: "numeric",
	    });
	  }

});
