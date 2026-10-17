function(a) {
  const text=(document.body?.innerText||'').replace(/\s+/g,' ').toUpperCase();
  const fail=message=>({state:'error',message});
  const list=document.querySelector('#myPageList');
  if(list) {
    if(!text.includes('GREATER HYDERABAD')) return fail('The reader is not showing Greater Hyderabad.');
    if(!text.includes(a.label)) return fail('The reader is not showing today’s issue. Try again after it is published.');
    const count=list.options.length;
    if(a.index>=count) return fail('The page list changed during preparation. Please retry.');
    if(list.selectedIndex!==a.index) {
      list.selectedIndex=a.index;
      list.dispatchEvent(new Event('change',{bubbles:true}));
      return {state:'wait'};
    }
    const img=document.querySelector('img.img_jpg');
    if(!img||!img.complete||img.naturalWidth<1000||!img.src||img.src===a.previous) return {state:'wait'};
    return {state:'page',url:img.src,count};
  }
  const cards=[...document.querySelectorAll('.ep_ed_pg')];
  if(cards.length) {
    const card=cards.find(e=>e.innerText.toUpperCase().includes('GREATER HYDERABAD'));
    if(!card)return fail('Greater Hyderabad is not listed by the publisher.');
    const image=card.querySelector('.ep_ed_img');
    if(!image||image.getAttribute('edate')!==a.date)return fail('Today’s Greater Hyderabad paper is not available yet.');
    if(!window.__spkChosen){window.__spkChosen=true;image.click();}
    return {state:'wait'};
  }
  if(location.pathname.includes('/login/login')) {
    const skip=[...document.querySelectorAll('a,button')].find(e=>e.textContent.trim()==='Skip registration');
    if(skip&&!window.__spkSkip){window.__spkSkip=true;skip.click();return {state:'wait'};}
    return {state:'login'};
  }
  return {state:'wait'};
}
